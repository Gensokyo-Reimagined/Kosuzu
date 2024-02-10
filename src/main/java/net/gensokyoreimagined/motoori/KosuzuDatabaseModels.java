package net.gensokyoreimagined.motoori;

public class KosuzuDatabaseModels {
    public static class Language {
        private final String code;
        private final String nativeName;
        private final String englishName;

        public Language(String code, String nativeName, String englishName) {
            this.code = code;
            this.nativeName = nativeName;
            this.englishName = englishName;
        }

        public String getCode() { return code; }
        public String getNativeName() { return nativeName; }
        public String getEnglishName() { return englishName; }
    }
}
