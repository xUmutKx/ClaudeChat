#!/usr/bin/env python3
"""Generates the extra hats (ic_hat_*, ic_stat_mascot_*) and mascot scenes (bg_scene_*) as vector drawables.
Hat art: rows y0..y6 of the 20x18 mascot canvas (head starts at y6), centred on x=10. Letters map to colours."""
import os
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res', 'drawable')

HATS = {
 'tophat': ({'k':'222222','r':'D32F2F'}, ['', 'kkkkkk','kkkkkk','kkkkkk','rrrrrr','kkkkkkkkkkkk']),
 'pirate': ({'k':'212121','w':'F5F5F5'}, ['', '..kkkk..','.kkkkkk.','kkkwwkkk','kkkkkkkkkk','kkkkkkkkkkkk']),
 'chef': ({'w':'FFFFFF','g':'DADADA'}, ['..wwww..','.wwwwwwww.','wwwwwwwwww','wwwwwwwwww','gggggggggg','wwwwwwwwww']),
 'cowboy': ({'b':'A9713A','d':'5D3A1A'}, ['', '', '..bbbbbb..','.bbbbbbbb.','bbddddddddbb','bbbbbbbbbbbbbbbb']),
 'beanie': ({'r':'E53935','w':'F5F5F5'}, ['', '..ww..','.rrrrrr.','rrrrrrrr','rrrrrrrrrr','wwwwwwwwwwww']),
 'viking': ({'m':'9EA7AD','h':'F5F0DC'}, ['', 'h............h','hh.mmmmmmmm.hh','h.mmmmmmmmmm.h','.mmmmmmmmmmmm.','mmmmmmmmmmmmmm']),
 'devil': ({'r':'E53935'}, ['', 'r........r','rr......rr','rr......rr','.rr....rr.','rrr....rrr']),
 'antlers': ({'b':'8D6E63'}, ['b.b......b.b','bbb......bbb','.bb......bb.','.bb......bb.','..bb....bb..','..bb....bb..']),
 'bunny': ({'w':'F5F5F5','p':'F8BBD0'}, ['ww....ww','wp....pw','wp....pw','wp....pw','ww....ww','ww....ww']),
 'ninja': ({'r':'D32F2F','g':'B0BEC5'}, ['', '', '', '', '', '', 'rrrrrggggrrrrr']),
 'sprout': ({'l':'66BB6A','g':'2E7D32','s':'6D4C41'}, ['', '.ll....gg.','llll..gggg','.lll..ggg.','....ss....','....ss....']),
 'flower': ({'p':'F06292','y':'FFD54F','g':'43A047'}, ['', '..pppp..','.ppyypp.','.ppyypp.','..pppp..','..gggg..']),
 'unicorn': ({'h':'F8E08E','y':'FFC107','p':'F48FB1'}, ['hh','yy','hh','yy','hhhh','pppppppp']),
 'propeller': ({'b':'1E88E5','r':'E53935','y':'FFD54F','k':'424242'}, ['yyyyyyyyyy','....kk....','..brrb..','.bbrrbb.','bbbrrbbb','bbbbbbbbbb']),
 'knight': ({'m':'B0BEC5','d':'546E7A','r':'E53935'}, ['', '....rr....','..mmrrmm..','.mmmmmmmm.','mmmmmmmmmm','mmmmmmmmmm','mddddddddm']),
 'graduate': ({'k':'263238','y':'FFC107'}, ['', '', '....kkkkkkkk....','kkkkkkkkkkkkkkkk','..kkkkkkkkkkkky.','...kkkkkkkkkk.y.']),
 'detective': ({'b':'B09A8F','d':'6D4C41'}, ['', '', '..bbbbbb..','.bbbbbbbb.','bdbdbdbdbd','dddddddddddd']),
 'witch': ({'p':'5E35B1','y':'FFC107','k':'212121'}, ['pp','pp','pppp','pppppp','pyyyyyyp','kkkkkkkkkkkkkk']),
 'hardhat': ({'y':'FFC107','o':'FFA000'}, ['', '', '..yooy..','.yyooyy.','yyyyyyyy','yyyyyyyyyyyy']),
 'mushroom': ({'r':'E53935','w':'FFFFFF'}, ['', '..rrrr..','.rwrrwr.','rrrrwwrr','rrrrrrrrrr','rwwrrrrwwr']),
}

def runs(rows):
    """{colour: [(x, y, w)]} from the art rows, each row centred on a 20-wide canvas"""
    out = {}
    for y, row in enumerate(rows):
        if not row: continue
        x0 = (20 - len(row)) // 2
        x = 0
        while x < len(row):
            c = row[x]
            if c == '.': x += 1; continue
            w = 1
            while x + w < len(row) and row[x + w] == c: w += 1
            out.setdefault(c, []).append((x0 + x, y, w)); x += w
    return out

def path(rs): return ' '.join(f'M{x},{y}H{x+w}V{y+1}H{x}Z' for x, y, w in rs)

def hat_xml(cols, rows):
    body = ''.join(f'    <path android:fillColor="#FF{cols[c]}" android:pathData="{path(rs)}" />\n' for c, rs in runs(rows).items())
    return '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="20dp" android:height="18dp" android:viewportWidth="20" android:viewportHeight="18">\n' + body + '</vector>\n'

BODY = 'M3,2H17V9H3Z M1,4H3V7H1Z M17,4H19V7H17Z M4,9H6V12H4Z M7,9H9V12H7Z M11,9H13V12H11Z M14,9H16V12H14Z M6.5,5H7.5V6H6.5Z M12.5,5H13.5V6H12.5Z'
def stat_xml(rows):
    allr = [r for rs in runs(rows).values() for r in rs]
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp" android:height="24dp" android:viewportWidth="20" android:viewportHeight="20">\n'
            '    <group android:translateY="4">\n        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="' + BODY + '" />\n    </group>\n'
            '    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="' + path(allr) + '" />\n</vector>\n')

# ---- scenes: 48x48 vectors
def R(c, x, y, w, h): return f'M{x},{y}H{x+w}V{y+h}H{x}Z', c
def C(c, cx, cy, r): return f'M{cx-r},{cy}a{r},{r} 0 1 1 {2*r},0a{r},{r} 0 1 1 {-2*r},0z', c
def T(c, a, b, d, e, f, g): return f'M{a},{b}L{d},{e}L{f},{g}Z', c
def stars(c, pts): return ''.join(f'M{x},{y}h1v1h-1Z' for x, y in pts), c

SC = {
 'space': [R('0B0B2B',0,0,48,48), stars('FFFFFF',[(5,6),(14,12),(40,8),(33,20),(8,30),(42,36),(22,5),(28,40),(3,42)]), C('7E57C2',36,14,6), C('B39DDB',34,12,2), C('FFB74D',10,38,5), R('FFE082',5,38,10,1)],
 'snow': [R('B3D4F2',0,0,48,48), C('FFFFFF',8,8,1), C('FFFFFF',30,6,1), C('FFFFFF',40,16,1), C('FFFFFF',18,14,1), R('F4F8FF',0,34,48,14), T('E3EEF9',0,34,12,22,24,34), T('FFFFFF',12,22,10,26,14,26), T('E3EEF9',22,34,36,18,48,34), T('FFFFFF',36,18,33,24,39,24)],
 'desert': [R('FFE0A3',0,0,48,48), C('FF7043',36,12,6), T('E5B26B',-8,48,18,26,44,48), T('D79B52',20,48,40,32,60,48), R('C98C46',0,42,48,6)],
 'sunset': [R('FF7E5F',0,0,48,24), R('FEB47B',0,18,48,10), C('FFE082',24,28,9), R('3B2A5A',0,28,48,20), R('5A3E7A',0,32,48,2), R('5A3E7A',0,38,48,1)],
 'mountains': [R('9ED8FF',0,0,48,48), T('6B7C93',-6,40,14,14,34,40), T('8697AE',14,40,32,8,54,40), T('FFFFFF',32,8,28,15,36,15), T('FFFFFF',14,14,10,22,18,22), R('4E8F4E',0,38,48,10)],
 'city': [R('87CEEB',0,0,48,48), R('607D8B',3,18,9,30), R('455A64',14,10,10,38), R('78909C',26,22,8,26), R('546E7A',36,14,9,34), stars('FFEE58',[(5,22),(8,22),(16,14),(20,14),(16,20),(20,20),(28,26),(31,26),(38,18),(41,18),(38,24),(41,24)])],
 'castle': [R('B3E5FC',0,0,48,48), R('66BB6A',0,38,48,10), R('9E9E9E',8,20,32,18), R('757575',4,14,8,24), R('757575',36,14,8,24), R('9E9E9E',4,12,2,2), R('9E9E9E',8,12,2,2), R('9E9E9E',36,12,2,2), R('9E9E9E',40,12,2,2), R('5D4037',20,28,8,10), T('E53935',2,14,8,6,14,14), T('E53935',34,14,40,6,46,14)],
 'farm': [R('B3E5FC',0,0,48,48), C('FFEE58',40,8,5), R('7CB342',0,26,48,22), R('9CCC65',0,34,48,3), R('C62828',6,22,14,12), T('8D2B2B',4,22,13,14,22,22), R('FFEBEE',11,27,4,7), R('8D6E63',30,26,1,8), R('8D6E63',34,26,1,8), R('8D6E63',38,26,1,8), R('8D6E63',28,29,12,1)],
 'aurora': [R('0A1A2F',0,0,48,48), R('2E7D5B',0,8,48,5), R('26A69A',0,13,48,5), R('7E57C2',0,18,48,4), stars('FFFFFF',[(6,4),(20,3),(34,5),(42,2),(10,28),(38,30),(26,36)]), R('E8F1F8',0,40,48,8), T('0F2A3F',0,40,10,30,20,40), T('0F2A3F',24,40,36,28,48,40)],
 'volcano': [R('FFAB91',0,0,48,48), T('5D4037',4,48,24,16,44,48), R('FF5722',21,16,6,3), R('FF9800',22,10,4,6), R('FFCC80',23,5,2,5), R('4E342E',0,44,48,4), C('8D6E63',12,10,3), C('8D6E63',36,8,4)],
 'coral': [R('006994',0,0,48,48), R('0288A8',0,0,48,12), R('F4D58D',0,42,48,6), R('FF7043',6,26,3,16), R('FF7043',3,30,3,3), R('FF7043',9,30,3,3), R('EC407A',38,22,3,20), R('EC407A',35,26,3,3), R('EC407A',41,26,3,3), C('B3E5FC',16,16,1), C('B3E5FC',30,10,2), C('B3E5FC',24,22,1)],
 'moon': [R('0D1B3E',0,0,48,48), C('FFF59D',34,13,8), C('0D1B3E',37,11,7), stars('FFFFFF',[(6,6),(14,16),(22,8),(8,26),(40,30),(26,34),(18,40)]), T('142850',0,48,14,36,28,48), T('142850',20,48,38,32,56,48)],
 'rain': [R('6E7F8D',0,0,48,48), C('90A4AE',12,10,7), C('90A4AE',22,8,8), C('90A4AE',33,11,7), R('90A4AE',10,10,26,7), stars('B3E5FC',[(10,22),(18,28),(26,24),(34,30),(14,36),(30,38),(40,22),(6,32)]), R('546E7A',0,42,48,6)],
 'garden': [R('BBDEFB',0,0,48,48), R('81C784',0,30,48,18), R('A5D6A7',0,30,48,3), R('F06292',8,24,2,8), C('F06292',9,22,3), R('FFD54F',20,26,2,6), C('FFD54F',21,24,3), R('BA68C8',36,24,2,8), C('BA68C8',37,22,3), R('FFFFFF',14,10,10,4), R('FFFFFF',18,8,8,3)],
 'candy': [R('FFD1E8',0,0,48,48), C('FF80AB',10,14,6), C('FFFFFF',10,14,3), C('80D8FF',36,10,5), C('FFFFFF',36,10,2), C('FFF176',38,34,6), C('FFFFFF',38,34,3), R('CE93D8',0,40,48,8), R('F8BBD0',0,40,48,2), C('AED581',8,32,4)],
 'neon': [R('120A2A',0,0,48,48), R('FF2D95',0,30,48,1), R('00E5FF',0,34,48,1), R('FF2D95',0,38,48,1), R('00E5FF',0,44,48,1), R('3A1C71',0,31,48,17), C('FF2D95',24,22,9), R('120A2A',0,24,48,2), R('120A2A',0,28,48,1)],
 'autumn': [R('FFE0B2',0,0,48,48), R('A1887F',0,40,48,8), R('6D4C41',10,18,4,24), C('FF8F00',12,14,10), C('E65100',6,18,5), C('FFB300',19,19,5), R('8D6E63',36,24,3,18), C('D84315',37,20,7), C('FF7043',32,23,4), stars('FF8F00',[(24,34),(28,38),(4,36)])],
 'rainbow': [R('B3E5FC',0,0,48,48), C('E53935',24,40,22), C('FFB300',24,40,19), C('66BB6A',24,40,16), C('42A5F5',24,40,13), C('B3E5FC',24,40,10), R('FFFFFF',4,34,12,5), R('FFFFFF',32,36,12,4), R('66BB6A',0,44,48,4)],
 'meadow': [R('C8E6C9',0,0,48,48), R('7CB342',0,22,48,26), T('9CCC65',-10,48,16,24,40,48), T('AED581',14,48,40,28,66,48), C('FFEE58',40,8,4), stars('FFFFFF',[(10,34),(20,40),(34,32),(8,42),(40,42)]), stars('F06292',[(16,30),(28,36),(38,38)])],
 'clouds': [R('64B5F6',0,0,48,48), R('FFFFFF',4,30,22,6), R('FFFFFF',8,26,12,5), R('FFFFFF',24,12,20,5), R('FFFFFF',28,8,10,5), R('E3F2FD',0,42,48,6), R('FFFFFF',14,40,26,6)],
}

def scene_xml(ops):
    body = ''.join(f'    <path android:fillColor="#{c}" android:pathData="{p}" />\n' for p, c in ops)
    return '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">\n' + body + '</vector>\n'

if __name__ == '__main__':
    for n, (cols, rows) in HATS.items():
        open(os.path.join(RES, f'ic_hat_{n}.xml'), 'w').write(hat_xml(cols, rows))
        open(os.path.join(RES, f'ic_stat_mascot_{n}.xml'), 'w').write(stat_xml(rows))
    for n, ops in SC.items():
        open(os.path.join(RES, f'bg_scene_{n}.xml'), 'w').write(scene_xml(ops))
    print(len(HATS), 'hats', len(SC), 'scenes')
    kt = lambda names, f, pre: ''.join(f'        "{n}" -> R.drawable.{pre}{n}\n' for n in names)
    open('/tmp/outfit_hats.txt', 'w').write(kt(HATS, 'hat', 'ic_hat_'))
    open('/tmp/outfit_stats.txt', 'w').write(kt(HATS, 'stat', 'ic_stat_mascot_'))
    open('/tmp/outfit_scenes.txt', 'w').write(''.join(f'"{n}" -> R.drawable.bg_scene_{n}; ' for n in SC))
    print(' '.join(f'"{n}",' for n in HATS)); print(' '.join(f'"{n}",' for n in SC))
