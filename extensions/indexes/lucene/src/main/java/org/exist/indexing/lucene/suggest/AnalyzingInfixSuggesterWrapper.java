/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2019 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene.suggest;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AnalyzingInfixSuggesterWrapper extends Suggester {

    private AnalyzingInfixSuggester suggester;
    private Path dataDir;

    public AnalyzingInfixSuggesterWrapper(String id, String field, Element config, Path indexDir, Analyzer analyzer) throws DatabaseConfigurationException {
        super(id, field, config, indexDir, analyzer);

        dataDir = indexDir.resolve("suggest_" + id);
        try {
            if (Files.exists(dataDir)) {
                if (!Files.isDirectory(dataDir)) {
                    throw new DatabaseConfigurationException("Lucene suggestions location is not a directory: " +
                            dataDir.toAbsolutePath().toString());
                }
            } else {
                Files.createDirectories(dataDir);
            }
            final Directory directory = FSDirectory.open(dataDir.toFile());
            suggester = new AnalyzingInfixSuggester(LuceneIndex.LUCENE_VERSION_IN_USE, directory, analyzer);
        } catch(IOException e) {
            throw new DatabaseConfigurationException("Error initializing lucene suggestions with id " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    List<Lookup.LookupResult> lookup(CharSequence key, boolean onlyMorePopular, int num) throws IOException {
        return suggester.lookup(key, num, true, false);
    }

    @Override
    public void build(IndexReader reader, String field) throws IOException {
        suggester.build(getDictionary(reader, field));
    }

    @Override
    public void close() throws IOException {
        suggester.close();
    }

    @Override
    void remove() throws IOException {
        suggester.close();
        FileUtils.delete(dataDir);
    }
}