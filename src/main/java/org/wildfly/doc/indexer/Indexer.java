package org.wildfly.doc.indexer;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@Command(name = "indexer", mixinStandardHelpOptions = true, version = "indexer 1.0",
        description = "Create an index with the WildFly Documentation.")
public class Indexer implements Callable<Integer> {
    private final static Logger LOG = Logger.getAnonymousLogger();
    private Map<SectionKey, List<String>> genIndex = new HashMap<>();
    record SectionKey(String title, String href, String url) {}
    public record IndexEntry(String title, String url, String content) {}

    @Option(names = "--doc-base-dir", required = true, description = "Path of the WildFly Documentation directory")
    private Path docBaseDir;

    @Option(names = "--output-file-path", description = "Path of generated index.", defaultValue = "./wildfly-doc-index.json")
    private Path outputFile;

    @Option(names = "--scan-dirs", split = ",", description = "Comma separated list of the directory names relative to --doc-base-dir that will be scanned to find html files to be processed", defaultValue = "38,prospero,bootablejar,galleon,galleon-plugins")
    private Path[] scanDirs;

    @Option(names = "--exclude-dirs", split = ",", description = "Directory names that if found will be skipped", defaultValue = "downloads,feature-pack,images")
    private Path[] excludeDirs;

    @Override
    public Integer call() throws Exception {
        for (Path rootDir : scanDirs) {
            processRootDir(rootDir, excludeDirs);
        }

        writeIndexToJson(outputFile);

        return 0;
    }

    private void processRootDir(Path rootDir, Path[] excludeDirs) throws IOException {
        Set<String> excludeNames = Arrays.stream(excludeDirs)
                .map(Path::toString)
                .collect(Collectors.toSet());

        Files.walkFileTree(docBaseDir.resolve(rootDir), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (excludeNames.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".html")) {
                    processFile(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    void processFile(Path htmlFile) throws IOException {
        LOG.info("Processing " + htmlFile);
        Document doc = Jsoup.parse(htmlFile, "UTF-8");
        cleanHtml(doc);
        processContent(doc, docBaseDir.relativize(htmlFile).toString());
    }

    void processContent(Document doc, String url) {
        Element contentDiv = doc.selectFirst("div#content");
        if (contentDiv == null) {
            LOG.warning("No content div found in document, continue with next file" );
            return;
        }

        String pageTitle = "Unknown";
        Element title = doc.selectFirst("head > title");
        if (title != null) {
            pageTitle = title.text().trim();
        }

        SectionKey rootSectionKey = new SectionKey(pageTitle, "", url);
        this.genIndex.put(rootSectionKey, new ArrayList<>());
        processData(contentDiv, rootSectionKey, this.genIndex, url);
    }

    void processData(Element element, SectionKey contextSection, Map<SectionKey, List<String>> index, String url) {
        // special case for section 0, title is not in a child of this element, it is directly on it
        if (element.id().equals("content")) {
            for (Element child : element.children()) {
                if (child.classNames().contains("sect0")) {
                    SectionKey sectionKey = processTitleHref(child, url);
                    if (sectionKey != null) {
                        index.putIfAbsent(sectionKey, new ArrayList<>());
                        contextSection = sectionKey;
                    }
                    break;
                }
            }
        } else {
            Set<String> classNames = element.classNames();

            // if the current tag has sect1, sect2, sect3 and so on, we get from there the section key that will
            // represent the information indexed for this section
            // We skip special case sectionbody, which generally is a div with additional content
            if (classNames.stream().anyMatch(c -> c.startsWith("sect") && !c.equals("sectionbody"))) {
                SectionKey sectionKey = processTitleHref(element.firstElementChild(), url);
                if (sectionKey != null) {
                    index.putIfAbsent(sectionKey, new ArrayList<>());
                    contextSection = sectionKey;
                }
            } else if ( // These are the html tags from where we obtain the text to be indexed
                    classNames.contains("paragraph")
                    || classNames.contains("ulist")
                    || classNames.contains("tableblock")
            ) {
                String text = element.text().trim();
                index.get(contextSection).add(text);
                return;
            }
        }

        // Continue from here going deeper until there a paragraph to extract the info
        for (Element child : element.children()) {
            processData(child, contextSection, index, url);
        }
    }

    SectionKey processTitleHref(Element element, String url) {
        SectionKey sectionKey = null;
        if (element != null && element.tagName().startsWith("h")) {
            String title = element.text();
            String href = "";

            // Find the anchor with class "anchor" inside the heading
            Element anchorElement = element.selectFirst("a.anchor");
            if (anchorElement != null) {
                href = anchorElement.attr("href");
            }

            // Create the section key and initialize the list in the index
            sectionKey = new SectionKey(title, href, url);
        }
        return sectionKey;
    }

    private void cleanHtml(Document doc) {
        doc.select("script").remove();
        doc.select("style").remove();
        doc.select("nav").remove();
        doc.select("footer").remove();
        doc.select("img").remove();

        doc.select(".listingblock").remove();
        doc.select(".literalblock").remove();
        doc.select(".navigation").remove();
        doc.select(".nav").remove();
        doc.select(".menu").remove();
        doc.select(".image").remove();
        doc.select("#toc").remove();
        doc.select(".breadcrumb").remove();
        doc.select(".admonitionblock").remove();
    }

    private List<IndexEntry> convertToIndexEntries() {
        List<IndexEntry> entries = new ArrayList<>();
        for (Map.Entry<SectionKey, List<String>> entry : this.genIndex.entrySet()) {
            SectionKey key = entry.getKey();
            List<String> paragraphs = entry.getValue();

            String fullUrl = key.url() + key.href();
            String content = String.join(" ", paragraphs);

            entries.add(new IndexEntry(key.title(), fullUrl, content));
        }
        return entries;
    }

    void writeIndexToJson(Path outputFile) throws IOException {
        List<IndexEntry> entries = convertToIndexEntries();
        ObjectMapper mapper = new ObjectMapper();
        String jsonContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries);
        Files.writeString(outputFile, jsonContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        LOG.info("Index written to JSON file: " + outputFile);
    }
}


