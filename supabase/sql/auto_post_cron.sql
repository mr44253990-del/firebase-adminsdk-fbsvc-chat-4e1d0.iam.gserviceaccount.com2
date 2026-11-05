-- Convo Chat: Auto AI posts + automatic poll expiry, every 10 minutes with Supabase Cron.
-- Run in Supabase Dashboard -> SQL Editor AFTER deploying the auto-ai-post Edge Function.
-- It reuses the Vault secrets created by presence_cron.sql (project_url, presence_cron_secret),
-- so run presence_cron.sql first. Running this again is safe (same job name = update).

create extension if not exists pg_cron;
create extension if not exists pg_net;

select cron.schedule(
  'convo-auto-ai-post',
  '*/10 * * * *',
  $job$
  select net.http_post(
    url := (select decrypted_secret from vault.decrypted_secrets where name = 'project_url')
           || '/functions/v1/auto-ai-post/cron',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'presence_cron_secret')
    ),
    body := jsonb_build_object('source', 'supabase-cron', 'at', now()),
    timeout_milliseconds := 10000
  ) as request_id;
  $job$
);

-- Check:
--   select jobid, jobname, schedule, active from cron.job where jobname = 'convo-auto-ai-post';
--   select status, return_message, start_time from cron.job_run_details
--     where jobid = (select jobid from cron.job where jobname = 'convo-auto-ai-post')
--     order by start_time desc limit 5;
-- Stop:
--   select cron.unschedule('convo-auto-ai-post');
