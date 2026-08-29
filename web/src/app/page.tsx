import { AppShell } from "@/components/AppShell";
import { ensureSeed } from "@/lib/seed";

export const dynamic = "force-dynamic";

export default async function Page() {
  // Bootstraps the demo world on first paint so the client never sees an empty shell.
  await ensureSeed().catch(() => {});
  return (
    <div className="h-[100dvh] w-full">
      <AppShell />
    </div>
  );
}
