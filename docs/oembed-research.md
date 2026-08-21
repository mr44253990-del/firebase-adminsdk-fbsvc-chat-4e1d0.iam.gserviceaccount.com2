# oEmbed implementation notes

Source repository: https://github.com/itteco/oembed-api
Specification reference: https://oembed.com/

## Contract used by the importer

A consumer requests an HTTP GET to a provider's oEmbed endpoint with `url`, optional `format=json`, and optional `maxwidth`/`maxheight` parameters. Provider discovery is performed by reading the source page's `<link rel="alternate" type="application/json+oembed" href="...">` tag, resolving the href against the source URL, and requesting JSON metadata.

Relevant response fields are `type`, `version`, `title`, `author_name`, `author_url`, `provider_name`, `provider_url`, `thumbnail_url`, `thumbnail_width`, `thumbnail_height`, `url`, `html`, `width`, and `height`. Video/rich embeds use `html`; photo responses use `url` plus thumbnail dimensions; link responses may only provide metadata.

## Security and product constraints

Only HTTPS source URLs are accepted. Imported HTML must not be inserted directly into Compose text or an unrestricted page. The Android importer extracts only an HTTPS iframe source and stores a normalized iframe wrapper for an isolated WebView; arbitrary scripts, javascript URLs, and non-HTTPS sources are rejected. The bulk importer deduplicates URLs, ignores blank/comment lines, caps a single import at 500 links, and writes Firestore batches in chunks below the 500-operation batch limit.

The product cannot guarantee a direct downloadable video for every provider. When a provider exposes only an embed or thumbnail, the app should render the provider embed or a thumbnail/link fallback rather than scraping or re-hosting the media.
