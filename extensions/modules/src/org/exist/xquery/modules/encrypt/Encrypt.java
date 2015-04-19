package org.exist.xquery.modules.encrypt;

import org.exist.dom.QName;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.*;
import org.exist.security.Permission;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class Encrypt extends BasicFunction {

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("encrypt", EncryptModule.NAMESPACE_URI, EncryptModule.PREFIX),
                    "Get/create a cache using the specified name.",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("key", Type.BASE64_BINARY, Cardinality.ONE_OR_MORE, "The name of the cache to get/create"),
                        new FunctionParameterSequenceType("data", Type.BASE64_BINARY, Cardinality.ONE, "The name of the cache to get/create")
                    },
                    new FunctionParameterSequenceType("encrypted", Type.BASE64_BINARY, Cardinality.ONE, "the Java cache object with the given name.")
            ),
            new FunctionSignature(
                    new QName("decrypt", EncryptModule.NAMESPACE_URI, EncryptModule.PREFIX),
                    "Get/create a cache using the specified name.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("keystore", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the cache to get/create"),
                            new FunctionParameterSequenceType("storepass", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the cache to get/create"),
                            new FunctionParameterSequenceType("keypass", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the cache to get/create"),
                            new FunctionParameterSequenceType("alias", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the cache to get/create"),
                            new FunctionParameterSequenceType("data", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The name of the cache to get/create")
                    },
                    new FunctionParameterSequenceType("decrypted", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "the Java cache object with the given name.")
            )
    };

    public Encrypt(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        try {
            if (isCalledAs("encrypt")) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ((BinaryValue)args[0].itemAt(0)).streamBinaryTo(bos);
                final byte[] keyEncoded = bos.toByteArray();
                final BinaryValue dataVal = (BinaryValue) args[1].itemAt(0);

                final X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(keyEncoded);
                final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                final Key publicKey = keyFactory.generatePublic(publicKeySpec);

                final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);

                bos = new ByteArrayOutputStream();
                final CipherOutputStream cos = new CipherOutputStream(bos, cipher);
                dataVal.streamBinaryTo(cos);
                cos.close();
                return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(bos.toByteArray()));
            } else {
                final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
                DocumentImpl storeDoc = context.getBroker().getResource(XmldbURI.createInternal(args[0].getStringValue()), Permission.READ);

                try (InputStream is = context.getBroker().getBinaryResource((BinaryDocument)storeDoc)) {
                    store.load(is, args[1].getStringValue().toCharArray());
                } catch(IOException e) {
                    throw new XPathException(this, e.getMessage(), e);
                }
                Key key = store.getKey(args[3].getStringValue(), args[2].getStringValue().toCharArray());
                if (key instanceof PrivateKey) {
                    final BinaryValue dataVal = (BinaryValue) args[4].itemAt(0);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    dataVal.streamBinaryTo(bos);
                    final byte[] data = bos.toByteArray();

                    final Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    rsaCipher.init(Cipher.DECRYPT_MODE, key);
                    final byte[] symmetricKey = rsaCipher.doFinal(data, 0, 128);
                    System.out.println("Symmetric key: " + new String(symmetricKey));

                    DESKeySpec keySpec = new DESKeySpec(symmetricKey);
                    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
                    key = keyFactory.generateSecret(keySpec);

                    final Cipher cipher = Cipher.getInstance("DES");
                    cipher.init(Cipher.DECRYPT_MODE, key);
                    bos = new ByteArrayOutputStream();
                    final CipherOutputStream cos = new CipherOutputStream(bos, cipher);
                    cos.write(data, 128, data.length - 128);
                    cos.close();
                    return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(bos.toByteArray()));
                }
                throw new XPathException(this, "No key found for alias " + args[2].getStringValue());
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new XPathException(this, e.getMessage());
        } catch (PermissionDeniedException e) {
            throw new XPathException(this, e.getMessage());
        }
    }
}
