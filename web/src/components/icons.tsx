type P = { className?: string; size?: number };

const base = (size = 20) => ({
  width: size,
  height: size,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.7,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
});

export const IconHome = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V21h14V9.5" /><path d="M9.5 21v-6h5v6" /></svg>
);
export const IconChat = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M21 12a8 8 0 0 1-8 8H7l-4 3v-6.5A8 8 0 0 1 11 4h2a8 8 0 0 1 8 8Z" /><path d="M9 11h6M9 15h4" /></svg>
);
export const IconPeople = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="9" cy="8" r="3.4" /><path d="M2.8 20c.6-3.6 3.1-5.6 6.2-5.6s5.6 2 6.2 5.6" /><path d="M16.5 5.6a3.2 3.2 0 0 1 0 6.3" /><path d="M18 14.8c2 .8 3.2 2.6 3.5 5.2" /></svg>
);
export const IconBell = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M6.5 10a5.5 5.5 0 0 1 11 0c0 4 1.5 5.5 1.5 5.5H5S6.5 14 6.5 10Z" /><path d="M10 19a2 2 0 0 0 4 0" /></svg>
);
export const IconUser = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="12" cy="8" r="3.6" /><path d="M4.5 20.5c.7-4 3.7-6.2 7.5-6.2s6.8 2.2 7.5 6.2" /></svg>
);
export const IconGear = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="12" cy="12" r="3.2" /><path d="M12 2.8v2.6M12 18.6v2.6M4.6 12H2M22 12h-2.6M6.3 6.3 4.5 4.5M19.5 19.5l-1.8-1.8M17.7 6.3l1.8-1.8M4.5 19.5l1.8-1.8" /></svg>
);
export const IconHeart = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M12 20s-7.5-4.4-7.5-9.4A4.1 4.1 0 0 1 12 8a4.1 4.1 0 0 1 7.5 2.6C19.5 15.6 12 20 12 20Z" /></svg>
);
export const IconThumb = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M7 21V10l4.2-7 1.3.7a3 3 0 0 1 1.3 3.4L13 10h4.8a2.4 2.4 0 0 1 2.3 3l-1.7 6.2a2.4 2.4 0 0 1-2.3 1.8Z" /><path d="M7 10H4v11h3" /></svg>
);
export const IconFlame = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M12 21c3.6 0 6-2.4 6-5.6 0-4.4-4.4-6-4.4-10.4 0 0-3.6 1.6-3.6 5.6 0 1.6-1.2 2-1.6 2S7 11.4 7 10c-.8 1.4-1 2.8-1 5.4C6 18.6 8.4 21 12 21Z" /></svg>
);
export const IconSpark = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9Z" /><path d="M18.5 16.5l.8 2.1 2.2.9-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.9Z" /></svg>
);
export const IconSmile = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="12" cy="12" r="9" /><path d="M8.5 14.5a4.4 4.4 0 0 0 7 0" /><path d="M9 9.5h.01M15 9.5h.01" /></svg>
);
export const IconComment = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M20 11.5a7.5 7.5 0 0 1-7.5 7.5H9l-4 3v-5.6A7.5 7.5 0 0 1 12 4h.5A7.5 7.5 0 0 1 20 11.5Z" /></svg>
);
export const IconEye = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M2.5 12S6 5.8 12 5.8 21.5 12 21.5 12 18 18.2 12 18.2 2.5 12 2.5 12Z" /><circle cx="12" cy="12" r="2.9" /></svg>
);
export const IconSend = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M4 12l16-7-7 16-2.2-6.4Z" /><path d="M10.8 14.6 20 5" /></svg>
);
export const IconImage = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><rect x="3" y="4.5" width="18" height="15" rx="3.2" /><circle cx="8.5" cy="10" r="1.6" /><path d="M4 17l4.8-4.4L14 17l2.6-2.4L20 17.4" /></svg>
);
export const IconMic = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><rect x="9" y="3" width="6" height="11" rx="3" /><path d="M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3" /></svg>
);
export const IconPlus = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M12 5v14M5 12h14" /></svg>
);
export const IconClose = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M6 6l12 12M18 6 6 18" /></svg>
);
export const IconBack = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M14.5 5 8 12l6.5 7" /></svg>
);
export const IconSearch = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="11" cy="11" r="6.4" /><path d="M15.8 15.8 21 21" /></svg>
);
export const IconLock = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><rect x="4.5" y="10.5" width="15" height="10" rx="3" /><path d="M8.2 10.5V8a3.8 3.8 0 0 1 7.6 0v2.5" /></svg>
);
export const IconGlobe = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="12" cy="12" r="9" /><path d="M3.4 9.5h17.2M3.4 14.5h17.2" /><path d="M12 3c2.4 2.6 3.6 5.6 3.6 9s-1.2 6.4-3.6 9c-2.4-2.6-3.6-5.6-3.6-9S9.6 5.6 12 3Z" /></svg>
);
export const IconTrash = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M4.5 7h15M9.5 7V4.8h5V7M6.5 7l1 13h9l1-13" /><path d="M10.5 11v5.5M13.5 11v5.5" /></svg>
);
export const IconEdit = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M4 20h4l10-10-4-4L4 16Z" /><path d="M13.5 4.5 19.5 10.5" /></svg>
);
export const IconForward = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M13 5l7 7-7 7" /><path d="M20 12H8a4 4 0 0 0-4 4v3" /></svg>
);
export const IconReply = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M11 5 4 12l7 7" /><path d="M4 12h11a5 5 0 0 1 5 5v2" /></svg>
);
export const IconGroup = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="8" cy="9" r="2.8" /><circle cx="16" cy="9" r="2.8" /><path d="M2.5 19c.5-3 2.7-4.8 5.5-4.8S13 16 13.5 19" /><path d="M14 14.4c2.6-.4 5 1.4 5.6 4.6" /></svg>
);
export const IconBlock = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="12" cy="12" r="9" /><path d="M5.6 5.6 18.4 18.4" /></svg>
);
export const IconCheck = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M5 12.8 9.5 17 19 7" /></svg>
);
export const IconVerified = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M12 2.8l2.3 1.7 2.8-.3 1 2.7 2.4 1.5-.9 2.7.9 2.7-2.4 1.5-1 2.7-2.8-.3L12 21.2l-2.3-1.7-2.8.3-1-2.7L3.5 15.6l.9-2.7-.9-2.7 2.4-1.5 1-2.7 2.8.3Z" /><path d="M8.8 12.2 11 14.4l4.2-4.4" /></svg>
);
export const IconLogout = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M14 4.5H6.5A2 2 0 0 0 4.5 6.5v11a2 2 0 0 0 2 2H14" /><path d="M17 8.5 20.5 12 17 15.5M20.5 12H10" /></svg>
);
export const IconPalette = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M12 3a9 9 0 0 0 0 18 2.4 2.4 0 0 0 2.4-2.4c0-1.9 2-1.6 3.4-1.6A3.2 3.2 0 0 0 21 13.8C21 7.8 17 3 12 3Z" /><circle cx="8" cy="9.5" r="1.1" /><circle cx="12.5" cy="7.4" r="1.1" /><circle cx="7.4" cy="14.4" r="1.1" /></svg>
);
export const IconShield = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M12 3l7.5 3v5.5c0 4.6-3 8-7.5 9.5-4.5-1.5-7.5-4.9-7.5-9.5V6Z" /><path d="M8.8 12.2 11 14.4l4.2-4.4" /></svg>
);
export const IconInfo = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7.6h.01" /></svg>
);
export const IconLink = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M10 13.8a4 4 0 0 0 5.7 0l2.8-2.8a4 4 0 0 0-5.7-5.7L11.4 6.7" /><path d="M14 10.2a4 4 0 0 0-5.7 0L5.5 13a4 4 0 0 0 5.7 5.7l1.4-1.4" /></svg>
);
export const IconNote = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><rect x="4.5" y="3.5" width="15" height="17" rx="3" /><path d="M8.5 8.5h7M8.5 12h7M8.5 15.5h4" /></svg>
);
