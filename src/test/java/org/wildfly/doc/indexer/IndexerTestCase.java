package org.wildfly.doc.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

public class IndexerTestCase {

    @Test
    public void testProcessData() throws IOException {
        Indexer indexer = new Indexer();

        Document doc = Jsoup.parse("""
                <html>
                <head><title>Test WildFly Documentation</title></head>
                <body>
                <div id="content">
                    <h1 class="sect0">
                        <a class="anchor" href="#section0"></a>
                        Different flavors of WildFly
                    </h1>
                    <div class="openblock partintro">
                       <div class="content">
                          <div class="paragraph">
                             <p>Open Block Paragraph One</p>
                          </div>
                          <div class="paragraph">
                             <p>Open Block Paragraph Two</p>
                          </div>
                       </div>
                    </div>
                    <div class="sect1">
                       <h2><a class="anchor" href="#section1"></a>1. Title</h2>
                       <div class="sectionbody">
                          <div class="paragraph">
                             <p>Section 1 Paragraph One</p>
                          </div>
                          <div class="paragraph">
                             <p>Section 1 Paragraph two</p>
                          </div>
                          <div class="sect2">
                             <h3><a class="anchor" href="#section2"></a>1.1. Title</h3>
                             <div class="paragraph">
                               <p>Section 1.1 Paragraph</p>
                             </div>
                             <div class="sect3">
                                <h4><a class="anchor" href="#section3"></a> 1.1.1. Title</h4>
                                <div class="paragraph">
                                   <p>Section 1.1.1 Paragraph</p>
                                </div>
                             </div>
                          </div>
                       </div>
                    </div>
                </div>
                </body>
                </html>
                """);

        indexer.processContent(doc, "test.html");

        Path outputFile = Paths.get("target", "test-index.json");
        indexer.writeIndexToJson(outputFile);

        assertTrue(Files.exists(outputFile), "Index JSON file should be created");

        // Read and parse the JSON content
        String jsonContent = Files.readString(outputFile);
        ObjectMapper mapper = new ObjectMapper();
        List<Indexer.IndexEntry> entries = mapper.readValue(jsonContent, new TypeReference<>() {});

        // Verify the correct number of entries (main page + 4 sections)
        assertEquals(5, entries.size(), "Should have 5 index entries");

        // Verify the main page entry
        Indexer.IndexEntry mainEntry = entries.stream()
                .filter(e -> e.title().equals("Test WildFly Documentation"))
                .findFirst()
                .orElse(null);

        assertNotNull(mainEntry, "Main page entry should exist");
        assertEquals("test.html", mainEntry.url(), "Main page URL should be correct");
        assertTrue(mainEntry.content().isEmpty(), "Main page should have empty content");

        // Verify the sect0 entry
        Indexer.IndexEntry sect0Entry = entries.stream()
                .filter(e -> e.title().equals("Different flavors of WildFly"))
                .findFirst()
                .orElse(null);
        assertNotNull(sect0Entry, "Section 0 entry should exist");
        assertEquals("test.html#section0", sect0Entry.url(), "Section 0 URL should include anchor");
        assertTrue(sect0Entry.content().contains("Open Block Paragraph One"), "Section 0 should contain paragraph content");
        assertTrue(sect0Entry.content().contains("Open Block Paragraph Two"), "Section 0 should contain paragraph content");

        // Verify the sect1 entry
        Indexer.IndexEntry sect1Entry = entries.stream()
                .filter(e -> e.title().equals("1. Title") && e.url().equals("test.html#section1"))
                .findFirst()
                .orElse(null);
        assertNotNull(sect1Entry, "Section 1 entry should exist");
        assertTrue(sect1Entry.content().contains("Section 1 Paragraph One"), "Section 1 should contain paragraph content");
        assertTrue(sect1Entry.content().contains("Section 1 Paragraph two"), "Section 1 should contain paragraph content");

        // Verify the sect2 entry
        Indexer.IndexEntry sect2Entry = entries.stream()
                .filter(e -> e.title().equals("1.1. Title"))
                .findFirst()
                .orElse(null);
        assertNotNull(sect2Entry, "Section 2 entry should exist");
        assertEquals("test.html#section2", sect2Entry.url(), "Section 2 URL should include anchor");
        assertTrue(sect2Entry.content().contains("Section 1.1 Paragraph"), "Section 2 should contain paragraph content");

        // Verify the sect3 entry
        Indexer.IndexEntry sect3Entry = entries.stream()
                .filter(e -> e.title().equals("1.1.1. Title"))
                .findFirst()
                .orElse(null);
        assertNotNull(sect3Entry, "Section 3 entry should exist");
        assertEquals("test.html#section3", sect3Entry.url(), "Section 3 URL should include anchor");
        assertTrue(sect3Entry.content().contains("Section 1.1.1 Paragraph"), "Section 3 should contain paragraph content");
    }
}
