package tv.miqat.installer;

/** Immutable host colours, with a canonical fallback palette. */
public final class InstallerPalette {
    public static final InstallerPalette DEFAULT = new InstallerPalette(
            InstallerColors.LAPIS_900, InstallerColors.LAPIS_800, InstallerColors.LAPIS_700,
            InstallerColors.GOLD_400, InstallerColors.GOLD_200,
            InstallerColors.PLASTER_100, InstallerColors.MIST_400);

    public final int ground, surface, raised, accent, accentLight, ink, muted, lineSoft, accentSoft;

    public InstallerPalette(int ground, int surface, int raised, int accent, int accentLight,
            int ink, int muted) {
        this.ground = ground;
        this.surface = surface;
        this.raised = raised;
        this.accent = accent;
        this.accentLight = accentLight;
        this.ink = ink;
        this.muted = muted;
        lineSoft = (ink & 0xffffff) | 0x40000000;
        accentSoft = (accent & 0xffffff) | 0x26000000;
    }
}
