/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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

package org.exist.indexing.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.CharSlice;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Map;

/**
 * Configuration for a field definition nested inside a lucene index configuration element.
 * A field has a name and content returned by an XQuery expression. It may be associated with
 * an analyzer, could have a type and may be stored or not.
 *
 * @author Wolfgang Meier
 */
public class LuceneFieldConfig extends AbstractFieldConfig {

    private final static String ATTR_FIELD_NAME = "name";
    private final static String ATTR_TYPE = "type";
    private final static String ATTR_STORE = "store";
    private final static String ATTR_ANALYZER = "analyzer";

    protected String fieldName;
    protected int type = Type.STRING;
    protected boolean store = true;
    protected Analyzer analyzer= null;

    LuceneFieldConfig(LuceneConfig config, Element configElement, Map<String, String> namespaces, AnalyzerConfig analyzers) throws DatabaseConfigurationException {
        super(config, configElement, namespaces);

        fieldName = configElement.getAttribute(ATTR_FIELD_NAME);
        if (fieldName == null || fieldName.length() == 0) {
            throw new DatabaseConfigurationException("Invalid config: attribute 'name' must be given");
        }

        final String typeStr = configElement.getAttribute(ATTR_TYPE);
        if (typeStr != null && typeStr.length() > 0) {
            try {
                this.type = Type.getType(typeStr);
            } catch (XPathException e) {
                throw new DatabaseConfigurationException("Invalid type declared for field " + fieldName + ": " + typeStr);
            }
        }

        final String storeStr = configElement.getAttribute(ATTR_STORE);
        if (storeStr != null && storeStr.length() > 0) {
            this.store = storeStr.equalsIgnoreCase("yes") || storeStr.equalsIgnoreCase("true");
        }

        final String analyzerOpt = configElement.getAttribute(ATTR_ANALYZER);
        if (analyzerOpt != null && analyzerOpt.length() > 0) {
            analyzer = analyzers.getAnalyzerById(analyzerOpt);
            if (analyzer == null) {
                throw new DatabaseConfigurationException("Analyzer for field " + fieldName + " not found");
            }
        }
    }

    @Nonnull
    public String getName() {
        return fieldName;
    }

    @Nullable
    @Override
    public Analyzer getAnalyzer() {
        return analyzer;
    }

    @Override
    void build(DBBroker broker, DocumentImpl document, NodeId nodeId, Document luceneDoc, CharSequence text) {
        try {
            doBuild(broker, document, nodeId, luceneDoc, text);
        } catch (XPathException e) {
            LOG.warn("XPath error while evaluating expression for field named '" + fieldName + "': " + expression +
                    ": " + e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            LOG.warn("Permission denied while evaluating expression for field named '" + fieldName + "': " + expression, e);
        }
    }

    @Override
    void processResult(Sequence result, Document luceneDoc) throws XPathException {
        for (SequenceIterator i = result.unorderedIterator(); i.hasNext(); ) {
            final String text = i.nextItem().getStringValue();
            final Field field = convertToField(text);
            if (field != null) {
                luceneDoc.add(field);
            }
        }
    }

    @Override
    void processText(CharSequence text, Document luceneDoc) {
        final Field field = convertToField(text.toString());
        if (field != null) {
            luceneDoc.add(field);
        }
    }

    private Field convertToField(String content) {
        try {
            switch (type) {
                case Type.INTEGER:
                case Type.LONG:
                case Type.UNSIGNED_LONG:
                    long lvalue = Long.parseLong(content);
                    return new LongField(fieldName, lvalue, LongField.TYPE_STORED);
                case Type.INT:
                case Type.UNSIGNED_INT:
                case Type.SHORT:
                case Type.UNSIGNED_SHORT:
                    int ivalue = Integer.parseInt(content);
                    return new IntField(fieldName, ivalue, IntField.TYPE_STORED);
                case Type.DECIMAL:
                case Type.DOUBLE:
                    double dvalue = Double.parseDouble(content);
                    return new DoubleField(fieldName, dvalue, DoubleField.TYPE_STORED);
                case Type.FLOAT:
                    float fvalue = Float.parseFloat(content);
                    return new FloatField(fieldName, fvalue, FloatField.TYPE_STORED);
                case Type.DATE:
                    DateValue dv = new DateValue(content);
                    long dl = dateToLong(dv);
                    return new LongField(fieldName, dl, LongField.TYPE_STORED);
                case Type.TIME:
                    TimeValue tv = new TimeValue(content);
                    long tl = timeToLong(tv);
                    return new LongField(fieldName, tl, LongField.TYPE_STORED);
                case Type.DATE_TIME:
                    DateTimeValue dtv = new DateTimeValue(content);
                    String dateStr = dateTimeToString(dtv);
                    return new TextField(fieldName, dateStr, Field.Store.YES);
                default:
                    return new TextField(fieldName, content, store ? Field.Store.YES : Field.Store.NO);
            }
        } catch (NumberFormatException | XPathException e) {
            // wrong type: ignore
        }
        return null;
    }

    private static long dateToLong(DateValue date) {
        final XMLGregorianCalendar utccal = date.calendar.normalize();
        return ((long)utccal.getYear() << 16) + ((long)utccal.getMonth() << 8) + ((long)utccal.getDay());
    }

    private static long timeToLong(TimeValue time) {
        return time.getTimeInMillis();
    }

    private static String dateTimeToString(DateTimeValue dtv) {
        final XMLGregorianCalendar utccal = dtv.calendar.normalize();
        final StringBuilder sb = new StringBuilder();
        formatNumber(utccal.getMillisecond(), 3, sb);
        formatNumber(utccal.getSecond(), 2, sb);
        formatNumber(utccal.getMinute(), 2, sb);
        formatNumber(utccal.getHour(), 2, sb);
        formatNumber(utccal.getDay(), 2, sb);
        formatNumber(utccal.getMonth(), 2, sb);
        formatNumber(utccal.getYear(), 4, sb);
        return sb.toString();
    }

    private static void formatNumber(int number, int digits, StringBuilder sb) {
        int count = 0;
        long n = number;
        while (n > 0) {
            final int digit = '0' + (int)n % 10;
            sb.insert(0, (char)digit);
            count++;
            if (count == digits) {
                break;
            }
            n = n / 10;
        }
        if (count < digits) {
            for (int i = count; i < digits; i++) {
                sb.insert(0, '0');
            }
        }
    }
}
