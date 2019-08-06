/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.indexing.lucene.analyzers;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.cn.ChineseAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.el.GreekAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.fa.PersianAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.hi.HindiAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.sv.SwedishAnalyzer;
import org.apache.lucene.analysis.tr.TurkishAnalyzer;
import org.apache.lucene.util.Version;

import java.util.HashMap;
import java.util.Map;

public class MultiLanguageAnalyzer extends DelegatingAnalyzerWrapper{

    private final Analyzer defaultAnalyzer;
    private final Map<String, Analyzer> langAnalyzers;
    private final String DEFAULT_LANG_ANALYZER = "en";
    private String language;

    public MultiLanguageAnalyzer(Version version) {
        this("en");
    }

    public MultiLanguageAnalyzer(String language) {
        super(PER_FIELD_REUSE_STRATEGY);
        this.language = language;
        this.langAnalyzers = new HashMap();
        langAnalyzers.put("ar", new ArabicAnalyzer());
        langAnalyzers.put("cn", new ChineseAnalyzer());
        langAnalyzers.put("cz", new CzechAnalyzer());
        langAnalyzers.put("de", new GermanAnalyzer());
        langAnalyzers.put("el", new GreekAnalyzer());
        langAnalyzers.put("en", new EnglishAnalyzer());
        langAnalyzers.put("es", new SpanishAnalyzer());
        langAnalyzers.put("fa", new PersianAnalyzer());
        langAnalyzers.put("fr", new FrenchAnalyzer());
        langAnalyzers.put("hi", new HindiAnalyzer());
        langAnalyzers.put("it", new ItalianAnalyzer());
        langAnalyzers.put("nl", new DutchAnalyzer());
        langAnalyzers.put("pt", new PortugueseAnalyzer());
        langAnalyzers.put("ru", new RussianAnalyzer());
        langAnalyzers.put("sv", new SwedishAnalyzer());
        langAnalyzers.put("tr", new TurkishAnalyzer());

        this.defaultAnalyzer = langAnalyzers.get("en");
    }

    public void setLanguage(String language) {
        if (!langAnalyzers.containsKey(language)) {
            // TODO log
            this.language = DEFAULT_LANG_ANALYZER;
        } else {
            this.language = language;
        }
    }

    protected Analyzer getWrappedAnalyzer(String fieldName) {
        Analyzer analyzer = (Analyzer)this.langAnalyzers.get(language);
        return analyzer != null ? analyzer : this.defaultAnalyzer;
    }

    public String toString() {
        return "MultiLanguageAnalyzer (" + this.language+ ", default=" + this.defaultAnalyzer + ")";
    }

}
