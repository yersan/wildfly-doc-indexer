# WildFly Documentation Indexer

The WildFly Documentation Indexer is a Maven-based tool designed to extract searchable content from WildFly documentation HTML files and generate a client-side search index. This tool processes HTML documentation files generated from AsciiDoc sources and creates a JSON index that can be consumed by JavaScript search plugins for fast, client-side document searching.

## Prerequisites

- **Java 21 or higher**
- **Apache Maven 3.9.0 or higher**
- **WildFly documentation HTML files** (generated from .adoc sources)

## Building the Project

To build the wildfly-doc-indexer project from source:

```bash
cd wildfly-doc-indexer
mvn clean package
```

This command will:
- Clean any previous build artifacts
- Compile the Java source code
- Run any unit tests
- Package the application into an executable JAR file
- Generate the final artifact: `target/wildfly-doc-indexer-1.0-SNAPSHOT.jar`

## Running the Indexer

After building the project, you can execute the indexer to generate the search index, for example for a https://github.com/wildfly/wildfly.github.io clone available on your local filesystem:

```bash
java -jar ./target/wildfly-doc-indexer-1.0-SNAPSHOT.jar --doc-base-dir=/wildfly.github.io
```
By default, this command will scan the `/wildfly.github.io` and will generate an index with the name `./wildfly-doc-index.json`.

The above command would be useful for developing purposes, but in a real example you will need specify which directories you want to scan or subdirectories you want to skip. The following are two examples of this:

* Generate the WIldFly 39 documentation for a wildfly.github.io clone located at /wildfly.github.io:

```bash
java -jar ./target/wildfly-doc-indexer-1.0-SNAPSHOT.jar \
--doc-base-dir=/wildfly.github.io \
--scan-dirs=39,prospero,bootablejar,galleon,galleon-plugins
```

### Command Line Arguments
```bash
$ java -jar ./target/wildfly-doc-indexer-1.0-SNAPSHOT.jar --help 
Usage: indexer [-hV] [--output-file-path=<outputFile>] --work-dir=<workDir>
               [--exclude-dirs=<excludeDirs>[,<excludeDirs>...]]...
               [--scan-dirs=<rootDirs>[,<rootDirs>...]]...
Create an index with the WildFly Documentation.
      --exclude-dirs=<excludeDirs>[,<excludeDirs>...]
                             Directory names that if found will be skipped
  -h, --help                 Show this help message and exit.
      --output-file-path=<outputFile>
                             Path of generated index.
      --scan-dirs=<rootDirs>[,<rootDirs>...]
                             Documentation directories that will be scanned
  -V, --version              Print version information and exit.
      --work-dir=<workDir>   Path of the WildFly Documentation directory
```

### How It Works

When executed with the above command:

1. **Source Discovery**: The indexer scans the work directory (`/wildfly.github.io/`) for HTML documentation files. There is a set of default directories that will be scanned. This default set can be overwritten with `--scan-dirs`
2. **Content Extraction**: It parses each HTML file to extract:
   - Document titles
   - Main content text (excluding navigation, headers, footers)
   - Document URLs/paths
3. **Index Generation**: Creates a structured JSON index containing the extracted information. 
4. **Output Creation**: Writes the index file. By default, it will create the following file `./wildfly-doc-index.json`. It can be overwritten with `--output-file-path`.

## Generated Index Format

The indexer produces a JSON file containing an array of document objects. Each object includes:

```json
[
  {
    "url": "path/to/document.html",
    "title": "Document Title",
    "content": "Extracted document content for searching..."
  }
]
```

This index format is optimized for client-side search libraries like FlexSearch, enabling:
- **Fast text searching** across all documentation
- **Title and content matching** with relevance scoring
- **Minimal bandwidth usage** through efficient JSON structure
