package net.moriis.moriisexpansions.sound;

/** Stable wire IDs: never reorder these entries. Paths follow Minecraft lowercase rules. */
public enum OrganInstrument {
    PLAIN("plain"), THUMPY("thumpy"), TUTTI("tutti"), GRAND_JEU("grand_jeu"), GOSPEL("gospel");
    private final String path;
    OrganInstrument(String path) { this.path = path; }
    public String path() { return path; }
    public String translationKey() { return "gui.moriisexpansions.organ.instrument." + path; }
    public static boolean isValid(int id) { return id >= 0 && id < values().length; }
    public static OrganInstrument fromId(int id) {
        if (!isValid(id)) throw new IllegalArgumentException("Invalid organ instrument: " + id);
        return values()[id];
    }
}
