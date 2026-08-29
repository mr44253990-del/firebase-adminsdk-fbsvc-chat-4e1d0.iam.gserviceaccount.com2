export type Theme = {
  id: string;
  name: string;
  group: "signature" | "light" | "neon" | "nature";
  vars: Record<string, string>;
};

const mk = (
  id: string,
  name: string,
  group: Theme["group"],
  v: {
    p: string;
    s: string;
    t: string;
    bg: string;
    bg2: string;
    glass: string;
    glassBorder: string;
    fg: string;
    muted: string;
    shadow?: string;
  },
): Theme => ({
  id,
  name,
  group,
  vars: {
    "--p": v.p,
    "--s": v.s,
    "--t": v.t,
    "--bg": v.bg,
    "--bg2": v.bg2,
    "--glass": v.glass,
    "--glass-border": v.glassBorder,
    "--fg": v.fg,
    "--muted": v.muted,
    "--shadow": v.shadow ?? "0 24px 60px -20px rgba(0,0,0,.65)",
  },
});

export const THEMES: Theme[] = [
  mk("liquid-aurora", "Liquid Aurora", "signature", {
    p: "#8b7cff",
    s: "#3ce0ff",
    t: "#ff6fc7",
    bg: "#07081a",
    bg2: "#11132e",
    glass: "rgba(23,26,54,.55)",
    glassBorder: "rgba(255,255,255,.12)",
    fg: "#f3f2ff",
    muted: "#9ba0c4",
  }),
  mk("obsidian-neon", "Obsidian Neon", "neon", {
    p: "#a88bff",
    s: "#00f0d0",
    t: "#ff3daa",
    bg: "#000000",
    bg2: "#0a0b14",
    glass: "rgba(14,16,26,.62)",
    glassBorder: "rgba(255,255,255,.10)",
    fg: "#f5f4ff",
    muted: "#8d92b0",
  }),
  mk("glass-ocean", "Glass Ocean", "signature", {
    p: "#59e4ff",
    s: "#6b9cff",
    t: "#4fffc1",
    bg: "#00101a",
    bg2: "#052a3a",
    glass: "rgba(6,40,56,.55)",
    glassBorder: "rgba(180,240,255,.16)",
    fg: "#eafcff",
    muted: "#8fb6c6",
  }),
  mk("glass-rose", "Glass Rose", "signature", {
    p: "#ff8ecb",
    s: "#c98bff",
    t: "#ffcf7a",
    bg: "#160711",
    bg2: "#341530",
    glass: "rgba(52,20,44,.55)",
    glassBorder: "rgba(255,205,235,.16)",
    fg: "#fff0f8",
    muted: "#c69fb6",
  }),
  mk("royal-gold", "Royal Gold", "neon", {
    p: "#ffd166",
    s: "#ffa62b",
    t: "#fff0b8",
    bg: "#110b02",
    bg2: "#2a1c06",
    glass: "rgba(42,28,10,.58)",
    glassBorder: "rgba(255,220,150,.18)",
    fg: "#fff8e8",
    muted: "#c9b184",
  }),
  mk("cyber-lime", "Cyber Lime", "neon", {
    p: "#c8ff4d",
    s: "#55e6a5",
    t: "#b2f7ff",
    bg: "#06100a",
    bg2: "#0f2417",
    glass: "rgba(15,36,23,.58)",
    glassBorder: "rgba(200,255,140,.16)",
    fg: "#f1ffee",
    muted: "#9dbf9f",
  }),
  mk("midnight-orchid", "Midnight Orchid", "nature", {
    p: "#e3b9ff",
    s: "#b996ff",
    t: "#7ef0ff",
    bg: "#07030d",
    bg2: "#1b0f2c",
    glass: "rgba(28,16,46,.58)",
    glassBorder: "rgba(230,200,255,.14)",
    fg: "#f7f0ff",
    muted: "#b09cc4",
  }),
  mk("forest-glass", "Forest Glass", "nature", {
    p: "#8fe3a8",
    s: "#5fd6c4",
    t: "#ffe08a",
    bg: "#040d09",
    bg2: "#0e2419",
    glass: "rgba(14,36,25,.58)",
    glassBorder: "rgba(180,255,210,.14)",
    fg: "#eefff3",
    muted: "#93b7a1",
  }),
  mk("frosted-light", "Frosted Light", "light", {
    p: "#6554d9",
    s: "#0090b8",
    t: "#e0479f",
    bg: "#eef0fb",
    bg2: "#ffffff",
    glass: "rgba(255,255,255,.72)",
    glassBorder: "rgba(255,255,255,.9)",
    fg: "#171522",
    muted: "#5c6180",
    shadow: "0 24px 60px -24px rgba(60,60,120,.32)",
  }),
  mk("sunrise-light", "Sunrise Light", "light", {
    p: "#f4614e",
    s: "#ff9f45",
    t: "#7b5cff",
    bg: "#fff5ef",
    bg2: "#ffffff",
    glass: "rgba(255,255,255,.74)",
    glassBorder: "rgba(255,255,255,.92)",
    fg: "#241612",
    muted: "#7a5d55",
    shadow: "0 24px 60px -24px rgba(180,90,60,.28)",
  }),
];

export const DEFAULT_THEME = THEMES[0];

export function themeById(id: string | undefined | null): Theme {
  return THEMES.find((t) => t.id === id) ?? DEFAULT_THEME;
}

export function themeStyle(id: string | undefined | null): Record<string, string> {
  return themeById(id).vars;
}

export const ACCENTS: Record<string, [string, string]> = {
  aurora: ["#8b7cff", "#3ce0ff"],
  sunset: ["#ff7a59", "#ffc857"],
  candy: ["#ff6fc7", "#a88bff"],
  mint: ["#4fffc1", "#3ce0ff"],
  gold: ["#ffd166", "#ff8a3d"],
  violet: ["#a88bff", "#ff6fc7"],
  ice: ["#8fd8ff", "#c9b4ff"],
  lime: ["#c8ff4d", "#55e6a5"],
};

export const ACCENT_KEYS = Object.keys(ACCENTS);

export function accentGradient(key: string | undefined): string {
  const [a, b] = ACCENTS[key ?? "aurora"] ?? ACCENTS.aurora;
  return `linear-gradient(135deg, ${a}, ${b})`;
}
