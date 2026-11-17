"""Commons helpers: search audio files, and read a file's licence, author, duration and download url."""
import json, sys, urllib.parse, urllib.request
UA = {'User-Agent': 'IndieGameSoundSourcing/1.0 (sourcing CC0/CC-BY ambience for an offline game; python-urllib)'}
API = 'https://commons.wikimedia.org/w/api.php?'
def get(params):
    return json.load(urllib.request.urlopen(urllib.request.Request(API + urllib.parse.urlencode(params), headers=UA), timeout=60))
def search(q, n=15):
    r = get({'action': 'query', 'list': 'search', 'srnamespace': 6, 'srlimit': n, 'format': 'json', 'srsearch': q + ' filetype:audio'})
    return [x['title'] for x in r['query']['search']]
def info(titles):
    r = get({'action': 'query', 'prop': 'imageinfo', 'iiprop': 'url|extmetadata|size|mediatype', 'format': 'json', 'titles': '|'.join(titles)})
    out = []
    for p in r['query']['pages'].values():
        ii = p.get('imageinfo', [{}])[0]; m = ii.get('extmetadata', {})
        v = lambda k: (m.get(k, {}).get('value') or '').replace('\n', ' ')
        import re
        art = re.sub('<[^>]+>', '', v('Artist'))[:60]
        out.append((p['title'], v('LicenseShortName'), art, ii.get('duration'), ii.get('url')))
    return out
if __name__ == '__main__':
    mode = sys.argv[1]
    if mode == 'search':
        for q in sys.argv[2:]:
            print('==', q)
            for t in search(q): print('  ', t.encode('ascii', 'replace').decode())
    else:
        for t in info(sys.argv[2:]): print(' | '.join(str(x).encode('ascii', 'replace').decode() for x in t))
