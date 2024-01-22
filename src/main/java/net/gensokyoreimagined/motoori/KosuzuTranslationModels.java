package net.gensokyoreimagined.motoori;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Random;

public class KosuzuTranslationModels {
    private static final Random random = new Random();

    /**
     * Region for DeepL API
     */

    public static class DeepLRequest {
        @SerializedName("text")
        List<String> text;
        @SerializedName("target_lang")
        String targetLanguage;

        DeepLRequest(String text, String targetLanguage) {
            this.text = List.of(text);
            this.targetLanguage = targetLanguage;
        }
    }

    public static class DeepLResponse {
        @SerializedName("translations")
        DeepLTranslation[] translations;

        DeepLTranslation getTranslation() {
            return translations[0];
        }

    }

    public static class DeepLTranslation {
        @SerializedName("detected_source_language")
        String detectedSourceLanguage;
        @SerializedName("text")
        String text;
    }

    /**
     * Region for DeepLX/DeepL Mobile API
     */

    public static class DeepLMobileRequest {
        @SerializedName("jsonrpc")
        String jsonrpc;
        @SerializedName("method")
        String method;
        @SerializedName("params")
        DeepLMobileParams params;
        @SerializedName("id")
        long id;

        DeepLMobileRequest(String input, String targetLanguage) {
            jsonrpc = "2.0";
            method = "LMT_handle_texts";
            random.setSeed(System.currentTimeMillis());
            id = random.nextLong(8300000, 8399998) * 1000; // ??? why
            params = new DeepLMobileParams(input, targetLanguage);
        }
    }

    public static class DeepLMobileParams {
        @SerializedName("texts")
        List<DeepLMobileText> texts;
        @SerializedName("splitting")
        String splitting;
        @SerializedName("lang")
        DeepLMobileLanguage lang;
        @SerializedName("timestamp")
        long timestamp;
        @SerializedName("commonJobParams")
        DeepLMobileCommonJobParams commonJobParams;

        DeepLMobileParams(String input, String targetLanguage) {
            texts = List.of(new DeepLMobileText(input));
            splitting = "newlines";
            lang = new DeepLMobileLanguage(targetLanguage);

            // chat i have no idea why they implemented it like this (not the stream but the count)
            // see PyDeepLX for more info
            var numberOfIsInText = input.chars().filter(letter -> (char) letter == 'i').count() + 1;
            var actualTimestamp = System.currentTimeMillis();
            timestamp = numberOfIsInText == 1 ? actualTimestamp : actualTimestamp - actualTimestamp % numberOfIsInText + numberOfIsInText;

            commonJobParams = new DeepLMobileCommonJobParams();
        }
    }

    public static class DeepLMobileText {
        @SerializedName("text")
        String text;
        @SerializedName("requestAlternatives")
        int requestAlternatives;

        DeepLMobileText(String text) {
            this.text = text;
            requestAlternatives = 0;
        }
    }

    public static class DeepLMobileLanguage {
        @SerializedName("source_lang_user_selected")
        String sourceLangUserSelected;
        @SerializedName("target_lang")
        String targetLang;

        DeepLMobileLanguage(String targetLang) {
            this.targetLang = targetLang;
            sourceLangUserSelected = "auto";
        }
    }

    public static class DeepLMobileCommonJobParams {
        @SerializedName("wasSpoken")
        boolean wasSpoken;
        @SerializedName("transcribe_as")
        String transcribeAs;

        DeepLMobileCommonJobParams() {
            wasSpoken = false;
            transcribeAs = "";
        }
    }

    public static class DeepLMobileResponse {
        @SuppressWarnings("unused")
        @SerializedName("jsonrpc")
        String jsonrpc;
        @SuppressWarnings("unused")
        @SerializedName("id")
        long id;
        @SerializedName("result")
        DeepLMobileResult result;

        DeepLTranslation getTranslation() {
            var mobileTranslation = result.translations[0];

            var translation = new DeepLTranslation();
            translation.detectedSourceLanguage = result.lang;
            translation.text = mobileTranslation.text;

            return translation;
        }
    }

    public static class DeepLMobileResult {
        @SerializedName("texts")
        DeepLMobileTranslation[] translations;
        @SerializedName("lang")
        String lang;
        @SuppressWarnings("unused")
        @SerializedName("lang_is_confident")
        boolean langIsConfident;
        // Ignore detectedLanguages for now
    }

    public static class DeepLMobileTranslation {
        @SerializedName("text")
        String text;
        // ignore alternatives for now
    }
}
