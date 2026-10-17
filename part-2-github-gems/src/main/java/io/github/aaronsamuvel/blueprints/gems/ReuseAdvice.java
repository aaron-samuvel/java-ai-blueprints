package io.github.aaronsamuvel.blueprints.gems;

/**
 * What you may do with a gem's code, based only on its detected licence. This is a study aid, not legal advice:
 * always read the actual LICENSE file before copying code.
 */
public enum ReuseAdvice {

    /** Permissive licence (MIT, Apache-2.0, BSD): copying is allowed with attribution and the licence notice. */
    PERMISSIVE_WITH_ATTRIBUTION("copy allowed with attribution and the licence notice"),
    /** Copyleft licence (GPL, AGPL, LGPL, MPL): reuse brings obligations; check before copying into this MIT repo. */
    COPYLEFT_CHECK_FIRST("copyleft: check obligations before copying"),
    /** No licence detected: all rights reserved by default, so read and learn but do not copy code. */
    READ_ONLY("no licence detected: study only, do not copy code");

    private final String text;

    ReuseAdvice(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    public static ReuseAdvice forLicence(String spdx) {
        String id = spdx == null ? "" : spdx.trim().toUpperCase();
        if (id.isEmpty() || id.equals("NONE") || id.equals("NOASSERTION")) return READ_ONLY;
        if (id.startsWith("MIT") || id.startsWith("APACHE-") || id.startsWith("BSD-") || id.equals("ISC")) {
            return PERMISSIVE_WITH_ATTRIBUTION;
        }
        if (id.contains("GPL") || id.startsWith("MPL-") || id.startsWith("EPL-")) return COPYLEFT_CHECK_FIRST;
        return READ_ONLY; // unknown licence: be conservative
    }
}
