# Playlist tools

## `filter_m3u.py`

Headless filter for `lista_fusionada.m3u` inspired by
[kamalsoft/m3u-editor](https://github.com/kamalsoft/m3u-editor):

- Fast M3U parse (same idea as `FastM3UParser`)
- URL dedupe keeping richest metadata
- Async HTTP health check (`ValidationWorker`-style HEAD + GET fallback)

```bash
python3 tools/filter_m3u.py lista_fusionada.m3u -o lista_fusionada.m3u
```
