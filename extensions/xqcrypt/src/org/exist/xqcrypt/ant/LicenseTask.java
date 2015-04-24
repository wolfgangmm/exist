package org.exist.xqcrypt.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.exist.security.UUIDGenerator;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DateTimeValue;

import javax.crypto.*;
import javax.crypto.spec.DESKeySpec;
import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class LicenseTask extends Task {

    private File output;
    private File keystore;
    private String secret;
    private String alias;
    private String storepass;

    public void setKeystore(File path) {
        this.keystore = path;
    }

    public void setStorepass(String storepass) {
        this.storepass = storepass;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Override
    public void execute() throws BuildException {
        if (secret == null) {
            throw new BuildException("No input specified for license task");
        }

        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream is = new FileInputStream(keystore)) {
                store.load(is, storepass.toCharArray());
            } catch (CertificateException | IOException e) {
                e.printStackTrace();
            }
            java.security.cert.Certificate cert = store.getCertificate(alias);
            PublicKey publicKey = cert.getPublicKey();

            final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            SecretKey symmetricKey = generateSymmetricKey();
            byte[] encodedKey = cipher.doFinal(symmetricKey.getEncoded());
            log("Encoded key size = " + encodedKey.length);

            String uuid = UUIDGenerator.getUUID();
            log("UUID = " + uuid);

            String licenseData =
                    "<license>" +
                    "   <secret>" + secret + "</secret>" +
                    "   <uuid>" + uuid + "</uuid>" +
                    "   <issued>" + new DateTimeValue().getStringValue() + "</issued>" +
                    "</license>";

            final byte[] encodedData = encodeSymmetric(symmetricKey, licenseData);
            final byte[] data = new byte[encodedKey.length + encodedData.length];
            System.arraycopy(encodedKey, 0, data, 0, encodedKey.length);
            System.arraycopy(encodedData, 0, data, encodedKey.length, encodedData.length);
            final Base64.Encoder base64Enc = Base64.getEncoder();
            final String base64 = base64Enc.encodeToString(data);

            try (FileOutputStream os = new FileOutputStream(output)) {
                os.write(base64.getBytes());
            }
        } catch (GeneralSecurityException | IOException | XPathException e) {
            e.printStackTrace();
            throw new BuildException("Error while generating license file: " + e.getMessage(), e);
        }
    }

    private SecretKey generateSymmetricKey() throws NoSuchPaddingException, NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("DES");
        keyGenerator.init(56);
        return keyGenerator.generateKey();
    }

    private byte[] encodeSymmetric(SecretKey key, String data) throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance("DES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CipherOutputStream cos = new CipherOutputStream(bos, cipher);
        OutputStreamWriter writer = new OutputStreamWriter(cos, "UTF-8");
        writer.write(data);
        writer.close();

        return bos.toByteArray();
    }
}
