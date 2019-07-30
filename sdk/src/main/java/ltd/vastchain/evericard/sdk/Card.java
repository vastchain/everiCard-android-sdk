package ltd.vastchain.evericard.sdk;

import androidx.arch.core.util.Function;

import org.apache.commons.lang.StringEscapeUtils;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.everitoken.sdk.java.EvtLink;
import io.everitoken.sdk.java.PublicKey;
import io.everitoken.sdk.java.Signature;
import io.everitoken.sdk.java.Utils;
import io.everitoken.sdk.java.dto.Transaction;
import ltd.vastchain.evericard.sdk.channels.EveriCardChannel;
import ltd.vastchain.evericard.sdk.command.ConfigurationRead;
import ltd.vastchain.evericard.sdk.command.ConfigurationWrite;
import ltd.vastchain.evericard.sdk.command.CreationEnd;
import ltd.vastchain.evericard.sdk.command.IdentityIssuerRead;
import ltd.vastchain.evericard.sdk.command.IdentityProducerRead;
import ltd.vastchain.evericard.sdk.command.ModifyPin;
import ltd.vastchain.evericard.sdk.command.PreferenceProducerRead;
import ltd.vastchain.evericard.sdk.command.PublicKeyRead;
import ltd.vastchain.evericard.sdk.command.SeedBackup;
import ltd.vastchain.evericard.sdk.command.SignHash;
import ltd.vastchain.evericard.sdk.command.VerifyPin;
import ltd.vastchain.evericard.sdk.response.ConfigurationResponse;
import ltd.vastchain.evericard.sdk.response.IdentityIssuerResponse;
import ltd.vastchain.evericard.sdk.response.IdentityProducerResponse;
import ltd.vastchain.evericard.sdk.response.PreferenceProducerResponse;
import ltd.vastchain.evericard.sdk.response.Response;
import ltd.vastchain.evericard.sdk.response.SeedBackupResponse;
import ltd.vastchain.evericard.sdk.response.SignResponse;

public class Card {
    private EveriCardChannel channel;

    public Card(EveriCardChannel channel) {
        this.channel = channel;
    }

    public static int getRecId(ECKey.ECDSASignature signature, byte[] hash, PublicKey publicKey) {
        Sha256Hash dataHash = Sha256Hash.wrap(hash);

        String refPubKey = publicKey.getEncoded(true);

        int recId = -1;
        for (int i = 0; i < 4; i++) {
            ECKey k = ECKey.recoverFromSignature(i, signature, dataHash, true);
            try {
                if (k != null && Utils.HEX.encode(k.getPubKey()).equals(refPubKey)) {
                    return i;
                }
            } catch (Exception ex) {
                // no need to handle anything here
            }
        }

        return recId;
    }

    public static Function<String, String> createSignProvider(int keyIndex) {
        return (signHash) -> String.format("%s, %d", signHash, keyIndex);
    }

    public PublicKey getPublicKeyByIndexAndSymbolId(int keyIndex, int symbolId) throws VCChipException {
        PublicKeyRead publicKeyRead = PublicKeyRead.byIndexAndSymbolId(keyIndex, symbolId);

        byte[] ret = channel.sendCommand(publicKeyRead);
        Response res = Response.of(ret);

        if (!res.isSuccessful()) {
            throw new VCChipException("get_publicKey_fail", String.format("could not get public key at index %s", keyIndex));
        }

        return new PublicKey(res.getContent());
    }

    public String getDisplayName() throws VCChipException {
        ConfigurationRead read = ConfigurationRead.readConfigurationItemData((byte) 0x0a);

        byte[] ret = channel.sendCommand(read);
        ConfigurationResponse res = new ConfigurationResponse(ret);

        if (!res.isSuccessful()) {
            throw new VCChipException("get_display_name_failed", "Failed to get display name");
        }

        String utf = new String(res.getConfigurationData(), StandardCharsets.UTF_8);
        return StringEscapeUtils.unescapeXml(utf);
    }

    public void setDisplayName(String name) throws VCChipException {
        ConfigurationWrite command = ConfigurationWrite.configureSettings(Arrays.asList(
                ConfigurationWrite.createTLVSetting((byte) 0x0a, StringEscapeUtils.escapeXml(name).getBytes())
        ), true);

        byte[] ret = channel.sendCommand(command);
        Response res = Response.of(ret);

        if (!res.isSuccessful()) {
            throw new VCChipException("set_display_name_failed", String.format("Failed to set display name to %s (%s).", name, Utils.HEX.encode(res.getStatus())));
        }
    }

    public boolean verifyPin(String pinInHex) {
        VerifyPin command = VerifyPin.of(Utils.HEX.decode(pinInHex));

        byte[] ret = channel.sendCommand(command);
        Response res = Response.of(ret);

        return res.isSuccessful();
    }

    public boolean modifyPin(byte[] oldPin, byte[] newPin) {
        ModifyPin command = ModifyPin.of(oldPin, newPin);
        byte[] ret = channel.sendCommand(command);

        Response res = Response.of(ret);

        return res.isSuccessful();
    }

    public String getIdentityProducer() throws VCChipException {
        byte[] random = Utils.random32Bytes();
        IdentityProducerRead command = IdentityProducerRead.of(random);

        byte[] ret = channel.sendCommand(command);
        IdentityProducerResponse res = new IdentityProducerResponse(ret, random);

        if (!res.isSuccessful()) {
            throw new VCChipException("get_identity_producer_failed", "Failed to get producer identity");
        }

        return Utils.HEX.encode(res.getContent());
    }

    public String getIdentityIssuer() {
        byte[] random = Utils.random32Bytes();
        IdentityIssuerRead command = IdentityIssuerRead.of(random);

        byte[] ret = channel.sendCommand(command);
        IdentityIssuerResponse res = new IdentityIssuerResponse(ret, random);
        return Utils.HEX.encode(res.getContent());
    }

    public String getPreferenceProducer() {
        PreferenceProducerRead command = new PreferenceProducerRead();

        byte[] ret = channel.sendCommand(command);
        PreferenceProducerResponse res = new PreferenceProducerResponse(ret);
        return Utils.HEX.encode(res.getContent());
    }

    public void endCreation() throws VCChipException {
        CreationEnd command = new CreationEnd();
        byte[] ret = channel.sendCommand(command);
        Response res = Response.of(ret);

        if (!res.isSuccessful()) {
            throw new VCChipException("creation_end_failed", String.format("Failed to on creation end command (%s).", Utils.HEX.encode(res.getStatus())));
        }
    }

    public String getSeedBackup() throws VCChipException {
        SeedBackup command = new SeedBackup();
        byte[] ret = channel.sendCommand(command);
        SeedBackupResponse res = new SeedBackupResponse(ret);

        if (!res.isSuccessful()) {
            throw new VCChipException("backup_seed_failed", String.format("Failed to back up seed (%s).", Utils.HEX.encode(res.getStatus())));
        }

        return Utils.HEX.encode(res.getSeed());
    }

    public String signHash(byte[] hash, int keyIndex, int symbolId) throws VCChipException {
        SignHash command = SignHash.of(keyIndex, hash);
        PublicKey publicKey = getPublicKeyByIndexAndSymbolId(keyIndex, symbolId);
        ECKey.ECDSASignature signature = null;
        BigInteger r = null;
        BigInteger s = null;

        // TODO: design counter to stop infinite loop
        while (true) {
            byte[] ret = channel.sendCommand(command);
            SignResponse res = new SignResponse(ret);

            if (!res.isSuccessful()) {
                throw new VCChipException("sign_hash_failed", String.format("Fail to sign hash (%s).", Utils.HEX.encode(res.getStatus())));
            }

            byte[] rawSignature = res.getSignature();
            r = new BigInteger(1, Arrays.copyOfRange(rawSignature, 0, 32));
            s = new BigInteger(1, Arrays.copyOfRange(rawSignature, 32, rawSignature.length));

            signature = new ECKey.ECDSASignature(r, s);

            // loop until get both r and s have 32 bytes
            if (r.toByteArray().length == 32 && s.toByteArray().length == 32 && signature.isCanonical()) {
                break;
            }
        }

        // TODO: handle recId can't be found
        int recId = Card.getRecId(signature, hash, publicKey);

        return new Signature(r, s, recId + 4 + 27).toString();
    }

    // TODO
    public List<Signature> signTransaction(Transaction trx, int keyIndex) {
        return new ArrayList<>();
    }

    // TODO
    public String SignEvtLink(String evtLink, int keyIndex, int symbolId) {
        // parse EvtLink
        byte[] decodedEvtlink = EvtLink.decode(evtLink);


        return "";
    }
}
