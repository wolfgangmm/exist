package org.exist.xqcrypt;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

public class EncryptModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/crypt";

    public final static String PREFIX = "crypt";
    public final static String INCLUSION_DATE = "2015-04-09";
    public final static String RELEASED_IN_VERSION = "eXist-2.3";

    private final static FunctionDef[] functions = {
            new FunctionDef(Encrypt.signatures[0], Encrypt.class),
            new FunctionDef(Encrypt.signatures[1], Encrypt.class)
    };

    public EncryptModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "A module for encrypting/decrypting binary resources";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}