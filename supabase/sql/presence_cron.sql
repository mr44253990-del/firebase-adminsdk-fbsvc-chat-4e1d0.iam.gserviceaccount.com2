-- Convo Chat: run the presence probe every 5 minutes with Supabase Cron.
-- Run in Supabase Dashboard -> SQL Editor AFTER deploying the Edge Function.
-- Replace the two placeholders. Running the script again is safe (it updates the job).

create extension if not exists pg_cron;
create extension if not exists pg_net;

-- 1) Store the project URL and cron secret in Supabase Vault (encrypted).
--    Use the SAME value you set as the PRESENCE_CRON_SECRET Edge Function secret.
do $$
begin
  if exists (select 1 from vault.secrets where name = 'project_url') then
    perform vault.update_secret((select id from vault.secrets where name = 'project_url'), 'https://YOUR-PROJECT-REF.supabase.co');
  else
    perform vault.create_secret('https://YOUR-PROJECT-REF.supabase.co', 'project_url');
  end if;
  if exists (select 1 from vault.secrets where name = 'presence_cron_secret') then
    perform vault.update_secret((select id from vault.secrets where name = 'presence_cron_secret'), 'PASTE-THE-SAME-PRESENCE_CRON_SECRET');
  else
    perform vault.create_secret('PASTE-THE-SAME-PRESENCE_CRON_SECRET', 'presence_cron_secret');
  end if;
end $$;

-- 2) Schedule the job (same name = update, never a second scheduler).
select cron.schedule(
  'convo-presence-probe',
  '*/5 * * * *',
  $job$
  select net.http_post(
    url := (select decrypted_secret from vault.decrypted_secrets where name = 'project_url')
           || '/functions/v1/presence-monitor/cron',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'presence_cron_secret')
    ),
    body := jsonb_build_object('source', 'supabase-cron', 'at', now()),
    timeout_milliseconds := 10000
  ) as request_id;
  $job$
);

-- 3) Check it:
--   select jobid, jobname, schedule, active from cron.job where jobname = 'convo-presence-probe';
--   select status, return_message, start_time from cron.job_run_details
--     where jobid = (select jobid from cron.job where jobname = 'convo-presence-probe')
--     order by start_time desc limit 5;
--   select id, status_code, content from net._http_response order by created desc limit 5;
-- Stop it:
--   select cron.unschedule('convo-presence-probe');
