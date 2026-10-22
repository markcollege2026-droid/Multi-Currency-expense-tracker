#!/usr/bin/env python3
"""Build the Ledger design mockups (single-file HTML, no external assets).

Run: python3 _build_mockups.py
Output: home_1.html, history_1.html, add-expense_1.html, converter_1.html,
        loans_1.html, settings_1.html, index.html
Design tokens live in .superdesign/design-system.md ("Ledger").
"""
import pathlib

OUT = pathlib.Path(__file__).parent

CSS = r"""
*{box-sizing:border-box;margin:0;padding:0}
:root{
  --paper:#F7F5EF; --surface:#FBF9F4; --card:#FFFFFF; --low:#F7F5EE; --cont:#F2F0E8;
  --high:#ECE9E0; --highest:#E6E3D9; --ink:#171B15; --ink2:#4C5148; --line:#CDD1C6;
  --primary:#0F5132; --primary-ink:#FFFFFF; --primary-cont:#D7ECDD; --primary-deep:#08361F;
  --sage:#5C6B60; --sage-cont:#E7EDE4; --amber:#8A5A12; --amber-cont:#FBE7C6;
  --error:#B3261E; --error-cont:#F9DEDC; --income:#1F7A4C; --warn:#B97C10; --focus:#FFC400;
  --r-xs:6px; --r-sm:10px; --r-md:14px; --r-lg:20px; --r-xl:28px;
  --font:-apple-system,BlinkMacSystemFont,"Segoe UI",Inter,Roboto,"Helvetica Neue",Arial,sans-serif;
}
html,body{background:#171B17;color:var(--ink);font-family:var(--font);-webkit-font-smoothing:antialiased}
body{padding:40px 20px}
.num{font-variant-numeric:tabular-nums;font-feature-settings:"tnum" 1}

/* ---------- device ---------- */
.stage{display:flex;justify-content:center}
.phone{width:390px;height:844px;background:#0B0F0C;border-radius:46px;padding:10px;
  box-shadow:0 40px 80px -24px rgba(0,0,0,.75),0 0 0 1px rgba(255,255,255,.06)}
.screen{position:relative;width:100%;height:100%;background:var(--paper);border-radius:37px;
  overflow:hidden;display:flex;flex-direction:column}
.statusbar{height:44px;flex:0 0 44px;display:flex;align-items:center;justify-content:space-between;
  padding:0 26px;font-size:12.5px;font-weight:600;color:var(--ink);letter-spacing:.2px}
.statusbar .right{display:flex;align-items:center;gap:5px}
.bars{display:flex;align-items:flex-end;gap:2px}
.bars i{display:block;width:3px;background:var(--ink);border-radius:1px}
.bars i:nth-child(1){height:4px}.bars i:nth-child(2){height:6px}
.bars i:nth-child(3){height:8px}.bars i:nth-child(4){height:10px}
.batt{width:22px;height:11px;border:1.5px solid var(--ink);border-radius:3px;position:relative;opacity:.9}
.batt:before{content:"";position:absolute;inset:1.5px 6px 1.5px 1.5px;background:var(--ink);border-radius:1px}

/* ---------- chrome ---------- */
.topbar{flex:0 0 auto;padding:2px 16px 12px;display:flex;align-items:flex-end;gap:10px;background:var(--paper)}
.topbar .titles{flex:1;min-width:0}
.eyebrow{font-size:11px;font-weight:700;letter-spacing:.9px;text-transform:uppercase;color:var(--ink2)}
.topbar h1{font-size:24px;line-height:30px;font-weight:700;letter-spacing:-.3px;margin-top:2px}
.topbar .who{font-size:12px;color:var(--ink2);margin-top:1px}
.roundbtn{width:40px;height:40px;border-radius:50%;background:var(--card);border:1px solid var(--line);
  display:grid;place-items:center;color:var(--ink)}
.roundbtn.solid{background:var(--primary);border-color:var(--primary);color:#fff}
.pill{display:inline-flex;align-items:center;gap:6px;height:30px;padding:0 12px;border-radius:999px;
  background:var(--card);border:1px solid var(--line);font-size:12px;font-weight:600;color:var(--ink)}
.pill.sel{background:var(--primary-cont);border-color:rgba(15,81,50,.22);color:var(--primary-deep)}
.content{flex:1;overflow-y:auto;padding:0 16px 18px;scrollbar-width:none}
.content::-webkit-scrollbar{display:none}
.content>*+*{margin-top:12px}

/* ---------- tabbar ---------- */
.tabbar{flex:0 0 76px;background:var(--surface);border-top:1px solid var(--line);
  display:grid;grid-template-columns:repeat(5,1fr);align-items:center;padding-bottom:6px}
.tab{display:flex;flex-direction:column;align-items:center;gap:3px;color:var(--ink2);font-size:10px;font-weight:600}
.tab .ico{width:56px;height:26px;border-radius:999px;display:grid;place-items:center}
.tab.on{color:var(--primary)}
.tab.on .ico{background:var(--primary-cont)}
.tab svg{width:18px;height:18px;stroke:currentColor;fill:none;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}

/* ---------- primitives ---------- */
.card{background:var(--card);border:1px solid var(--line);border-radius:var(--r-lg);padding:16px}
.card.tight{padding:12px}
.sect{display:flex;align-items:center;gap:10px;margin:16px 2px 0}
.sect h2{font-size:11px;font-weight:700;letter-spacing:.9px;text-transform:uppercase;color:var(--ink2);flex:1}
.sect a{font-size:12px;font-weight:700;color:var(--primary);text-decoration:none}
.hero{border-radius:var(--r-xl);padding:20px;color:#fff;position:relative;overflow:hidden;
  background:linear-gradient(145deg,#0F5132 0%,#1D6B45 100%)}
.hero .glow{position:absolute;width:220px;height:220px;border-radius:50%;right:-70px;top:-90px;
  background:radial-gradient(circle,rgba(255,255,255,.16),transparent 62%)}
.hero .eyebrow{color:rgba(255,255,255,.72)}
.hero .amount{font-size:44px;line-height:48px;font-weight:700;letter-spacing:-1.2px;margin-top:6px}
.hero .amount small{font-size:24px;font-weight:600;letter-spacing:-.4px;opacity:.9;margin-right:4px}
.hero .stats{display:flex;gap:16px;margin-top:16px;padding-top:14px;border-top:1px solid rgba(255,255,255,.18)}
.hero .stats div{flex:1}
.hero .stats span{display:block;font-size:10px;letter-spacing:.8px;text-transform:uppercase;color:rgba(255,255,255,.66)}
.hero .stats b{display:block;font-size:15px;margin-top:3px;font-weight:600}
.hero .foot{display:flex;align-items:center;gap:8px;margin-top:14px}
.hero .tag{font-size:11px;font-weight:600;color:#FFE7B4;background:rgba(255,255,255,.10);
  border:1px solid rgba(255,255,255,.18);border-radius:999px;padding:4px 10px;display:inline-flex;gap:5px}
.hero .adjust{margin-left:auto;font-size:12px;font-weight:700;color:#fff;border:1px solid rgba(255,255,255,.35);
  border-radius:999px;padding:6px 12px;text-decoration:none}
.btn{height:46px;border-radius:var(--r-md);display:flex;align-items:center;justify-content:center;gap:8px;
  font-size:14px;font-weight:700;letter-spacing:.1px;border:1px solid transparent}
.btn.primary{background:var(--primary);color:#fff}
.btn.tonal{background:var(--primary-cont);color:var(--primary-deep)}
.btn.ghost{background:var(--card);border-color:var(--line);color:var(--ink)}
.btn.sm{height:34px;font-size:12px;border-radius:var(--r-sm);padding:0 12px}
.rowline{display:flex;align-items:center;gap:12px;padding:10px 0}
.rowline+.rowline{border-top:.5px solid rgba(205,209,198,.75)}
.avatar{width:38px;height:38px;border-radius:12px;display:grid;place-items:center;font-size:13px;font-weight:800;flex:0 0 38px}
.rtitle{font-size:14px;font-weight:600;line-height:18px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.rmeta{font-size:11.5px;color:var(--ink2);margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ramt{margin-left:auto;text-align:right}
.ramt b{display:block;font-size:14px;font-weight:700;letter-spacing:-.1px}
.ramt span{font-size:10.5px;color:var(--ink2)}
.neg{color:var(--error)}
.pos{color:var(--income)}
.muted{color:var(--ink2)}
.bar{height:6px;border-radius:999px;background:var(--cont);overflow:hidden}
.bar i{display:block;height:100%;border-radius:999px;background:var(--primary)}
.chip{height:30px;padding:0 12px;border-radius:999px;border:1px solid var(--line);background:transparent;
  display:inline-flex;align-items:center;font-size:12px;font-weight:600;color:var(--ink2);white-space:nowrap}
.chip.on{background:var(--primary-cont);border-color:rgba(15,81,50,.22);color:var(--primary-deep);font-weight:700}
.chiprow{display:flex;gap:6px;overflow:hidden}
.field{background:var(--low);border:1px solid var(--line);border-radius:var(--r-sm);padding:8px 12px}
.field .lbl{font-size:10.5px;font-weight:700;letter-spacing:.6px;text-transform:uppercase;color:var(--ink2)}
.field .val{font-size:15px;font-weight:600;margin-top:2px}
.field.sel{display:flex;align-items:center;gap:10px}
.field.sel .val{flex:1}
.chev{color:var(--ink2);font-size:16px;font-weight:700}
.tile{background:var(--low);border-radius:var(--r-md);padding:12px;flex:1}
.tile .k{font-size:10.5px;font-weight:700;letter-spacing:.7px;text-transform:uppercase;color:var(--ink2)}
.tile .v{font-size:18px;font-weight:700;margin-top:3px;letter-spacing:-.3px}
.empty{background:var(--low);border-radius:var(--r-md);padding:18px;text-align:center;color:var(--ink2);font-size:12px}

/* ---------- dialog ---------- */
.scrim{position:absolute;inset:0;background:rgba(23,27,21,.42);backdrop-filter:blur(1.5px);
  display:flex;align-items:center;justify-content:center;padding:20px;z-index:5}
.dialog{width:100%;max-width:330px;background:var(--card);border-radius:20px;padding:20px;
  box-shadow:0 24px 48px -12px rgba(0,0,0,.35)}
.dialog h2{font-size:18px;line-height:24px;font-weight:700;letter-spacing:-.2px}
.dialog .hint{font-size:11.5px;color:var(--ink2);margin-top:6px;line-height:17px}
.dialog .stack{margin-top:14px;display:flex;flex-direction:column;gap:10px}
.dactions{margin-top:18px;display:flex;flex-wrap:wrap;gap:8px;justify-content:flex-end;align-items:center}
.dactions .btn{width:auto;padding:0 16px;height:40px}
.dactions .txt{font-size:13px;font-weight:700;color:var(--primary);padding:0 8px;height:40px;
  display:flex;align-items:center}
.dactions .txt.danger{color:var(--error)}
.dactions .btn.danger{background:var(--error);color:#fff}
.addmenu{background:var(--card);border:1px solid var(--line);border-radius:var(--r-md);padding:12px}
.txt.sm{font-size:11.5px;font-weight:600;color:var(--primary)}
hr{display:block}
@keyframes pop{from{transform:scale(.72);opacity:0}to{transform:scale(1);opacity:1}}
.badge-note{position:absolute;left:16px;right:16px;bottom:96px;background:rgba(255,255,255,.94);
  border:1px solid var(--line);border-radius:14px;padding:10px 12px;font-size:11px;color:var(--ink2);
  line-height:16px;z-index:6}
.badge-note b{color:var(--ink)}

/* ---------- gallery ---------- */
.gallery{max-width:1500px;margin:0 auto}
.gallery header{color:#F4F2EA;text-align:center;margin-bottom:34px}
.gallery header .eyebrow{color:#A9B6AA}
.gallery header h1{font-size:40px;line-height:44px;letter-spacing:-1.2px;margin:8px 0 10px;color:#F7F5EF}
.gallery header p{color:#AEBBAF;font-size:14px;max-width:620px;margin:0 auto;line-height:22px}
.grid{display:flex;flex-wrap:wrap;gap:56px 44px;justify-content:center}
.item{width:390px}
.frame{width:390px;height:767px;overflow:hidden;border-radius:38px}
.frame iframe{width:470px;height:924px;border:0;transform:scale(.83);transform-origin:top left}
.item h3{color:#E9E7DE;font-size:14px;font-weight:700;margin-top:18px;letter-spacing:.2px}
.item p{color:#9FAAA2;font-size:12px;margin-top:4px;line-height:18px}
.tokens{display:flex;flex-wrap:wrap;gap:10px;justify-content:center;margin:0 auto 40px;max-width:900px}
.sw{display:flex;align-items:center;gap:8px;color:#C9D2C9;font-size:11px;
  border:1px solid rgba(255,255,255,.10);border-radius:999px;padding:6px 12px}
.sw i{width:10px;height:10px;border-radius:3px;display:block}
.note{color:#8E9A90;font-size:11px;text-align:center;margin-top:34px;line-height:18px}
@media(max-width:520px){body{padding:20px 8px}.phone{width:100%;height:auto;aspect-ratio:390/844}
  .item{width:100%}.gallery header h1{font-size:28px;line-height:32px}}
"""

STATUS = """<div class="statusbar"><span>9:41</span><div class="right">
<span class="bars"><i></i><i></i><i></i><i></i></span><span class="batt"></span></div></div>"""

ICONS = {
"home": '<svg viewBox="0 0 24 24"><path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"/></svg>',
"list": '<svg viewBox="0 0 24 24"><path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"/></svg>',
"swap": '<svg viewBox="0 0 24 24"><path d="M4 8h13l-3-3M20 16H7l3 3"/></svg>',
"users": '<svg viewBox="0 0 24 24"><path d="M16 19v-1a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v1M13 7a3 3 0 1 1-6 0 3 3 0 0 1 6 0M17 5.5a3 3 0 0 1 0 5.8M21 19v-1a4 4 0 0 0-3-3.7"/></svg>',
"cog": '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2V21a2 2 0 1 1-4 0v-.1A1.7 1.7 0 0 0 7 19.4a1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.7 1.7 0 0 0 3 14.9H3a2 2 0 1 1 0-4h.1A1.7 1.7 0 0 0 4.6 7a1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.7 1.7 0 0 0 9 3.1h0A2 2 0 1 1 13 3v.1a1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9v0a1.7 1.7 0 0 0 1.6 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>',
"plus": '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
"back": '<svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg>',
}


def tabbar(active):
    items = [("home", "Home", "home"), ("history", "History", "list"),
             ("rates", "Rates", "swap"), ("loans", "Loans", "users"), ("settings", "Settings", "cog")]
    out = ['<nav class="tabbar">']
    for key, label, icon in items:
        cls = "tab on" if key == active else "tab"
        out.append(f'<div class="{cls}"><span class="ico">{ICONS[icon]}</span>{label}</div>')
    out.append("</nav>")
    return "".join(out)


def page(title, body, tabs=None, active=None, back=False, gallery=False):
    chrome_top = STATUS
    if back:
        chrome_top += ('<div class="topbar" style="align-items:center"><span class="roundbtn" '
                       f'style="border:none;background:transparent">{ICONS["back"]}</span>'
                       '<div class="titles"><h1 style="font-size:18px">New expense</h1></div>'
                       '<span class="pill sel">ILS ₪</span></div>')
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title} · Ledger</title><style>{CSS}</style></head>
<body>
<div class="stage"><div class="phone"><div class="screen">
{chrome_top}
{body}
{tabs or ''}
</div></div></div>
</body></html>"""


# ---------------------------------------------------------------- HOME
def home():
    body = f"""
<div class="topbar">
  <div class="titles">
    <div class="eyebrow">Tue, 21 Oct</div>
    <h1>Spending</h1>
  </div>
  <span class="pill sel">Main · ₪</span>
  <span class="roundbtn solid">{ICONS['plus']}</span>
</div>
<main class="content">
  <section class="hero">
    <div class="glow"></div>
    <div class="eyebrow">Total balance</div>
    <div class="amount num"><small>₪</small>12,480.25</div>
    <div class="stats">
      <div><span>This week</span><b class="num">₪1,284.50</b></div>
      <div><span>This month</span><b class="num">₪4,912.80</b></div>
    </div>
    <div class="foot">
      <span class="tag">● Rates updated 3h ago</span>
      <a class="adjust" href="#">Adjust</a>
    </div>
  </section>

  <div class="addmenu">
    <div class="btn primary" style="width:100%">+ Add</div>
    <div style="display:flex;gap:8px;margin-top:8px">
      <div class="btn primary" style="flex:1">Expense</div>
      <div class="btn ghost" style="flex:1;color:var(--income)">Deposit</div>
      <div class="btn ghost" style="flex:1">Withdraw</div>
    </div>
    <div class="hint" style="margin-top:8px">Deposit adds money to main (optionally funded from a cash
      balance); Withdraw moves main into a cash balance, converting automatically and accepting the
      amount in either currency.</div>
  </div>

  <section class="card">
    <div style="display:flex;align-items:baseline;gap:8px">
      <div class="eyebrow" style="flex:1">Total available</div>
      <div class="num" style="font-size:15px;font-weight:700">₪14,982.10</div>
    </div>
    <div class="rowline" style="padding-top:12px">
      <span class="avatar" style="background:var(--primary-cont);color:var(--primary-deep)">M</span>
      <div style="min-width:0"><div class="rtitle">Main / debit</div><div class="rmeta">Everyday spending</div></div>
      <div class="ramt"><b class="num">₪12,480.25</b></div>
    </div>
    <div class="rowline">
      <span class="avatar" style="background:var(--amber-cont);color:var(--amber)">C</span>
      <div style="min-width:0"><div class="rtitle">Cash</div><div class="rmeta">Cash · ILS · tap to edit</div></div>
      <div class="ramt"><b class="num">₪820.00</b></div>
    </div>
    <div style="display:flex;gap:12px;padding:0 0 6px 42px">
      <span class="txt sm">Deposit</span><span class="txt sm">Withdraw</span><span class="txt sm">Set aside</span>
    </div>
    <div class="rowline">
      <span class="avatar" style="background:var(--amber-cont);color:var(--amber)">€</span>
      <div style="min-width:0"><div class="rtitle">Euro wallet</div><div class="rmeta">Cash · EUR · tap to edit</div></div>
      <div class="ramt"><b class="num">€140.00</b></div>
    </div>
    <div style="display:flex;gap:12px;padding:0 0 6px 42px">
      <span class="txt sm">Deposit</span><span class="txt sm">Withdraw (₪→€)</span><span class="txt sm">Set aside</span>
    </div>
    <div class="rowline">
      <span class="avatar" style="background:var(--highest);color:var(--ink2)">🏦</span>
      <div style="min-width:0"><div class="rtitle">Savings jar</div><div class="rmeta">Cash · ILS · set aside · not counted in total</div></div>
      <div class="ramt"><b class="num muted">₪2,000.00</b></div>
    </div>
    <div style="display:flex;gap:12px;padding:0 0 6px 42px">
      <span class="txt sm">Deposit</span><span class="txt sm">Withdraw</span><span class="txt sm">Count in total</span>
    </div>
    <div class="rowline">
      <span class="avatar" style="background:var(--error-cont);color:var(--error)">V</span>
      <div style="min-width:0"><div class="rtitle">Visa •• 4421</div><div class="rmeta">Autopay 28 Oct · full</div></div>
      <div class="ramt"><b class="num neg">−₪318.15</b></div>
    </div>
    <div style="display:flex;gap:8px;margin-top:12px">
      <div class="btn ghost sm" style="flex:1">+ Add cash balance</div>
      <div class="btn ghost sm" style="flex:1">+ Add credit card</div>
    </div>
    <div class="hint" style="margin-top:10px">Total available = main + cash counted in totals (converted) − card debt.</div>
  </section>

  <div class="sect"><h2>Recent</h2><a href="#">See all</a></div>
  <section class="card tight" style="padding:4px 12px">
    <div class="rowline">
      <span class="avatar" style="background:#F3E7D2;color:#8A5A12">F</span>
      <div style="min-width:0"><div class="rtitle">Coffee</div><div class="rmeta">Food · Café Dizengoff</div></div>
      <div class="ramt"><b class="num neg">−₪18.00</b><span>08:12</span></div>
    </div>
    <div class="rowline">
      <span class="avatar" style="background:#E3EEDF;color:#2F6B44">G</span>
      <div style="min-width:0"><div class="rtitle">Groceries</div><div class="rmeta">Food · Shufersal</div></div>
      <div class="ramt"><b class="num neg">−₪236.40</b><span>Yesterday</span></div>
    </div>
    <div class="rowline">
      <span class="avatar" style="background:var(--primary-cont);color:var(--primary-deep)">S</span>
      <div style="min-width:0"><div class="rtitle">Salary — October</div><div class="rmeta">Deposit · external</div></div>
      <div class="ramt"><b class="num pos">+₪9,400.00</b><span>19 Oct</span></div>
    </div>
    <div class="rowline">
      <span class="avatar" style="background:#E6E9F2;color:#3C4C7A">T</span>
      <div style="min-width:0"><div class="rtitle">Metro card</div><div class="rmeta">Transport · €2.50 → ₪9.90</div></div>
      <div class="ramt"><b class="num neg">−₪9.90</b><span>19 Oct</span></div>
    </div>
  </section>

  <div class="sect"><h2>Top categories · October</h2><a href="#">Breakdown</a></div>
  <section class="card" style="display:flex;flex-direction:column;gap:12px">
    <div>
      <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:6px">
        <b>Food &amp; drink</b><span class="num muted">₪1,120 · 34%</span></div>
      <div class="bar"><i style="width:34%"></i></div>
    </div>
    <div>
      <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:6px">
        <b>Transport</b><span class="num muted">₪690 · 21%</span></div>
      <div class="bar"><i style="width:21%"></i></div>
    </div>
    <div>
      <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:6px">
        <b>Home</b><span class="num muted">₪590 · 18%</span></div>
      <div class="bar"><i style="width:18%"></i></div>
    </div>
  </section>

  <div class="sect"><h2>Last 7 days</h2><span class="muted" style="font-size:11px">avg ₪183 / day</span></div>
  <section class="card" style="display:flex;align-items:flex-end;gap:10px;height:96px;padding:14px 16px">
    <div style="flex:1;text-align:center"><div style="height:44px;display:flex;align-items:flex-end"><div style="width:100%;height:28px;border-radius:5px;background:var(--primary)"></div></div><div class="muted" style="font-size:9px;margin-top:6px">15</div></div>
    <div style="flex:1;text-align:center"><div style="height:44px;display:flex;align-items:flex-end"><div style="width:100%;height:16px;border-radius:5px;background:var(--primary);opacity:.85"></div></div><div class="muted" style="font-size:9px;margin-top:6px">16</div></div>
    <div style="flex:1;text-align:center"><div style="height:44px;display:flex;align-items:flex-end"><div style="width:100%;height:34px;border-radius:5px;background:var(--primary)"></div></div><div class="muted" style="font-size:9px;margin-top:6px">17</div></div>
    <div style="flex:1;text-align:center"><div style="height:44px;display:flex;align-items:flex-end"><div style="width:100%;height:22px;border-radius:5px;background:var(--primary)"></div></div><div class="muted" style="font-size:9px;margin-top:6px">18</div></div>
    <div style="flex:1;text-align:center"><div style="height:44px;display:flex;align-items:flex-end"><div style="width:100%;height:40px;border-radius:5px;background:var(--primary)"></div></div><div class="muted" style="font-size:9px;margin-top:6px">19</div></div>
    <div style="flex:1;text-align:center"><div style="height:44px;display:flex;align-items:flex-end"><div style="width:100%;height:12px;border-radius:5px;background:var(--primary)"></div></div><div class="muted" style="font-size:9px;margin-top:6px">20</div></div>
    <div style="flex:1;text-align:center"><div style="height:44px;display:flex;align-items:flex-end"><div style="width:100%;height:8px;border-radius:5px;background:var(--primary);opacity:.45"></div></div><div class="muted" style="font-size:9px;margin-top:6px">21</div></div>
  </section>

  <div class="sect"><h2>Tools</h2></div>
  <section class="card tight" style="padding:0 12px">
    <div class="rowline"><span class="avatar" style="background:var(--sage-cont);color:var(--sage)">↻</span>
      <div style="min-width:0"><div class="rtitle">Recurring payments</div><div class="rmeta">3 active · next 28 Oct</div></div><span class="chev">›</span></div>
    <div class="rowline"><span class="avatar" style="background:var(--sage-cont);color:var(--sage)">⇄</span>
      <div style="min-width:0"><div class="rtitle">Loans &amp; debts</div><div class="rmeta">₪1,240 I owe · ₪2,600 owed to me</div></div><span class="chev">›</span></div>
  </section>
</main>
"""
    return page("Home", body, tabs=tabbar("home"))


# ---------------------------------------------------------------- HISTORY
def history():
    def row(avatar, bg, fg, title, meta, amount, amount_cls, when):
        return f"""<div class="rowline">
      <span class="avatar" style="background:{bg};color:{fg}">{avatar}</span>
      <div style="min-width:0"><div class="rtitle">{title}</div><div class="rmeta">{meta}</div></div>
      <div class="ramt"><b class="num {amount_cls}">{amount}</b><span>{when}</span></div>
    </div>"""

    body = f"""
<div class="topbar">
  <div class="titles"><div class="eyebrow">October · ₪ spent 4,912.80</div><h1>History</h1></div>
  <span class="roundbtn">{ICONS['list']}</span>
</div>
<main class="content">
  <div class="field" style="display:flex;align-items:center;gap:10px">
    <span style="color:var(--ink2)">⌕</span>
    <span class="muted" style="font-size:14px;flex:1">Search note, amount, person…</span>
    <span class="pill sel">+ Add</span>
  </div>
  <div class="chiprow" style="margin-top:6px">
    <span class="chip on">Expense</span><span class="chip">Deposit</span><span class="chip">Withdraw</span>
  </div>
  <div class="chiprow">
    <span class="chip on">All</span><span class="chip">Expenses</span><span class="chip">Deposits</span>
    <span class="chip">Card pmts</span><span class="chip">Transfers</span><span class="chip">Food</span>
  </div>
  <div class="hint" style="padding:0 2px">“+ Add” pops out Expense / Deposit / Withdraw, exactly like Home.
    Rows show the time only while it is today, then the date. Tapping a row opens its details;
    editing is a choice, not a side effect of looking.</div>

  <div class="sect" style="margin-top:4px"><h2>Today · ₪254.40</h2></div>
  <section class="card tight" style="padding:4px 12px">
    {row("F", "#F3E7D2", "#8A5A12", "Coffee", "Food · Café Dizengoff", "−₪18.00", "neg", "08:12")}
    {row("G", "#E3EEDF", "#2F6B44", "Groceries", "Food · Shufersal", "−₪236.40", "neg", "07:40")}
  </section>

  <div class="sect"><h2>Yesterday · ₪1,026.85</h2></div>
  <section class="card tight" style="padding:4px 12px">
    {row("R", "#E6E9F2", "#3C4C7A", "Rent — November", "Home · bank transfer", "−₪980.00", "neg", "21 Oct")}
    {row("T", "#E9E4F2", "#5B4B86", "Metro card", "Transport · €2.50 → ₪9.90", "−₪9.90", "neg", "21 Oct")}
    {row("V", "#F3E7D2", "#8A5A12", "Card payment · Visa 4421", "Paid from main · debit", "−₪318.15", "neg", "21 Oct")}
    {row("→", "#F2F0E8", "#4C5148", "Withdrawal to Euro wallet", "Transfer · main → cash · ATM", "−₪400.00 ≈ +€100.00", "muted", "21 Oct")}
    {row("C", "#D7ECDD", "#08361F", "Refund · Amazon", "Shopping · returned", "+₪281.20", "pos", "21 Oct")}
  </section>

  <div class="sect"><h2>19 October · ₪9,390.10</h2></div>
  <section class="card tight" style="padding:4px 12px">
    {row("S", "#D7ECDD", "#08361F", "Salary — October", "Deposit · external", "+₪9,400.00", "pos", "19 Oct")}
    {row("T", "#E9E4F2", "#5B4B86", "Taxi", "Transport · Gett", "−₪62.00", "neg", "19 Oct")}
    {row("F", "#F3E7D2", "#8A5A12", "Lunch", "Food · Miznon", "−₪52.00", "neg", "19 Oct")}
  </section>

  <div class="sect"><h2>18 October · ₪184.00</h2></div>
  <section class="card tight" style="padding:4px 12px">
    {row("H", "#E3EEDF", "#2F6B44", "Pharmacy", "Health · Super-Pharm", "−₪124.00", "neg", "18 Oct")}
    {row("F", "#F3E7D2", "#8A5A12", "Dinner", "Food · HaBasta", "−₪60.00", "neg", "18 Oct")}
  </section>

  <div class="sect"><h2>Earliest this month</h2></div>
  <section class="card tight" style="padding:4px 12px">
    {row("U", "#E6E9F2", "#3C4C7A", "Internet", "Home · Bezeq", "−₪99.00", "neg", "1 Oct")}
    {row("W", "#E9E4F2", "#5B4B86", "Water", "Home · municipality", "−₪142.30", "neg", "1 Oct")}
  </section>
  <div style="height:6px"></div>
</main>
"""
    return page("History", body, tabs=tabbar("history"))


# ---------------------------------------------------------------- ADD EXPENSE
def add_expense():
    keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫"]
    keypad = "".join(
        f'<div style="height:52px;border-radius:12px;display:grid;place-items:center;font-size:20px;'
        f'font-weight:600;background:{"var(--cont)" if k in ".⌫" else "var(--low)"};color:var(--ink)">{k}</div>'
        for k in keys)
    cats = [("F", "Food", True), ("T", "Transport", False), ("H", "Home", False),
            ("S", "Shopping", False), ("C", "Health", False), ("+", "New", False)]
    chips = "".join(
        f'<span class="chip {"on" if on else ""}" style="height:34px;padding:0 12px;gap:6px">'
        f'<b style="font-weight:800">{n}</b>{label}</span>' for n, label, on in cats)
    body = f"""
<div class="topbar" style="align-items:center">
  <span class="roundbtn" style="border:none;background:transparent">{ICONS['back']}</span>
  <div class="titles"><h1 style="font-size:18px">New expense</h1></div>
  <span class="pill sel">ILS ₪</span>
</div>
<main class="content">
  <section class="card" style="padding:18px 16px;background:var(--card)">
    <div class="eyebrow" style="text-align:center">Amount in ILS</div>
    <div class="num" style="font-size:46px;line-height:54px;font-weight:700;letter-spacing:-1.4px;text-align:center">₪ 42.00</div>
    <div style="display:flex;justify-content:center;gap:8px;margin-top:8px">
      <span class="chip">€ EUR</span><span class="chip on">₪ ILS</span><span class="chip">$ USD</span>
    </div>
    <div style="display:flex;justify-content:center;gap:6px;margin-top:10px">
      <span class="muted" style="font-size:11px">rate 1 EUR = 3.7240 ILS · locked at save</span>
    </div>
  </section>

  <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:6px">{keypad}</div>

  <div class="sect" style="margin-top:2px"><h2>Category</h2><a href="#">Manage</a></div>
  <div style="display:flex;gap:6px;flex-wrap:wrap">{chips}</div>

  <div style="display:flex;flex-direction:column;gap:8px">
    <div class="field sel"><div style="flex:1"><div class="lbl">Paid from</div><div class="val">Main / debit</div></div><span class="chev">›</span></div>
    <div class="field sel"><div style="flex:1"><div class="lbl">Date</div><div class="val">Today, 21 Oct · 09:41</div></div><span class="chev">›</span></div>
    <div class="field"><div class="lbl">Note</div><div class="val" style="font-weight:400;color:var(--ink2)">Lunch with Dana</div></div>
  </div>
  <div class="btn primary" style="margin-top:4px">Save expense</div>
  <div class="btn ghost">Cancel</div>
</main>
"""
    return page("New expense", body)


# ---------------------------------------------------------------- ADD DEPOSIT
def add_deposit():
    body = """
<div class="topbar" style="align-items:center">
  <span class="roundbtn" style="border:none;background:transparent">""" + ICONS['back'] + """</span>
  <div class="titles"><h1 style="font-size:18px">Deposit</h1></div>
  <span class="pill sel">Main · ₪</span>
</div>
<main class="content">
  <section class="card">
    <div class="eyebrow">Amount</div>
    <div class="num" style="font-size:40px;line-height:48px;font-weight:700;letter-spacing:-1.2px">₪ 1,200.00</div>
    <div class="hint" style="margin-top:6px">Goes into the main account.</div>
  </section>

  <div class="eyebrow" style="padding:0 2px">Source</div>
  <div class="field sel"><div style="flex:1"><div class="lbl">Funded by</div>
    <div class="val">External — not from cash</div></div><span class="chev">›</span></div>
  <div class="chiprow">
    <span class="chip on">External</span><span class="chip">Cash · ILS</span><span class="chip">Euro wallet · EUR</span>
  </div>
  <div class="hint">External is the default: a salary or refund arriving from outside the app.
    Picking a cash balance turns the deposit into an internal transfer — the preview below
    shows exactly what leaves that balance.</div>

  <div class="card" style="background:var(--primary-cont);border-color:transparent">
    <div class="eyebrow" style="color:var(--primary-deep)">If funded from Euro wallet</div>
    <div class="num" style="font-size:20px;font-weight:700;margin-top:4px">€304.00 leaves cash</div>
    <div class="hint" style="color:var(--primary-deep)">≈ ₪1,200.00 at 1 EUR = 3.9473 ILS ·
      stored rate, both sides saved</div>
  </div>

  <div class="hint" style="color:var(--error)">More than the EUR balance holds</div>

  <div class="field"><div class="lbl">Note</div><div class="val" style="font-weight:400;color:var(--ink2)">October salary</div></div>

  <div class="btn primary">Save deposit</div>
  <div class="btn ghost">Cancel</div>
</main>
"""
    return page("Add deposit", body)


# ---------------------------------------------------------------- CONVERTER
def converter():
    pairs = "".join(f'<span class="chip {"on" if p=="USD → ILS" else ""}">{p}</span>'
                    for p in ["USD → ILS", "EUR → ILS", "GBP → ILS", "ILS → USD"])
    body = f"""
<div class="topbar">
  <div class="titles"><div class="eyebrow">Mid-market · 3h ago</div><h1>Converter</h1></div>
  <span class="roundbtn">↻</span>
</div>
<main class="content">
  <section class="card" style="padding:18px">
    <div style="display:flex;align-items:center;gap:8px">
      <span class="pill sel">🇺🇸 USD · US Dollar</span>
      <span style="flex:1"></span>
      <span class="muted" style="font-size:11px">You send</span>
    </div>
    <div class="num" style="font-size:40px;line-height:48px;font-weight:700;letter-spacing:-1.2px;margin-top:10px">100.00</div>
    <div class="muted" style="font-size:12px;margin-top:2px">one hundred US dollars</div>
  </section>

  <div style="display:flex;justify-content:center;margin:-26px 0 -26px;position:relative;z-index:2">
    <span style="width:44px;height:44px;border-radius:50%;background:var(--primary);color:#fff;display:grid;
      place-items:center;font-size:18px;box-shadow:0 6px 14px rgba(15,81,50,.28)">⇅</span>
  </div>

  <section class="card" style="padding:18px;background:var(--primary-cont);border-color:rgba(15,81,50,.18)">
    <div style="display:flex;align-items:center;gap:8px">
      <span class="pill" style="background:#fff">🇮🇱 ILS · Israeli shekel</span>
      <span style="flex:1"></span>
      <span style="font-size:11px;color:var(--primary-deep);opacity:.75">You get</span>
    </div>
    <div class="num" style="font-size:40px;line-height:48px;font-weight:700;letter-spacing:-1.2px;margin-top:10px;color:var(--primary-deep)">372.40</div>
    <div style="font-size:12px;margin-top:2px;color:var(--primary-deep);opacity:.72">no fees · mid-market rate</div>
  </section>

  <div class="card" style="display:flex;flex-direction:column;gap:4px">
    <div style="display:flex;justify-content:space-between;font-size:13px">
      <span class="muted">Rate</span><b class="num">1 USD = 3.7240 ILS</b>
    </div>
    <div style="display:flex;justify-content:space-between;font-size:13px">
      <span class="muted">Inverse</span><b class="num">1 ILS = 0.2685 USD</b>
    </div>
    <div style="display:flex;justify-content:space-between;font-size:13px">
      <span class="muted">Source</span><span>open.er-api.com · cached</span>
    </div>
  </div>

  <div class="sect"><h2>Last 7 days</h2><span class="pos" style="font-size:12px;font-weight:700">+0.8%</span></div>
  <section class="card" style="padding:12px 12px 8px">
    <svg viewBox="0 0 320 96" style="width:100%;height:96px;display:block">
      <defs><linearGradient id="fill" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="#0F5132" stop-opacity=".22"/>
        <stop offset="1" stop-color="#0F5132" stop-opacity="0"/></linearGradient></defs>
      <path d="M4 78 L56 66 L108 70 L160 52 L212 58 L264 34 L316 26 L316 92 L4 92 Z" fill="url(#fill)"/>
      <path d="M4 78 L56 66 L108 70 L160 52 L212 58 L264 34 L316 26" fill="none" stroke="#0F5132"
        stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
      <circle cx="316" cy="26" r="4.5" fill="#0F5132"/>
      <g font-size="9" fill="#4C5148" font-family="-apple-system,sans-serif">
        <text x="4" y="94">15 Oct</text><text x="150" y="94">18 Oct</text><text x="284" y="94">21 Oct</text>
      </g>
    </svg>
  </section>

  <div class="sect"><h2>Quick pairs</h2></div>
  <div class="chiprow" style="overflow-x:auto;padding-bottom:2px">{pairs}</div>

  <div class="btn primary">Add to ledger…</div>
  <div class="btn ghost">Refresh rates</div>
  <div style="height:4px"></div>
</main>
"""
    return page("Converter", body, tabs=tabbar("rates"))


# ---------------------------------------------------------------- LOANS
def loans():
    def loan(person, note, total, left, pct, due, state):
        badge = {"overdue": '<span style="font-size:10px;font-weight:800;letter-spacing:.6px;color:var(--error)">OVERDUE</span>',
                 "settled": '<span style="font-size:10px;font-weight:800;letter-spacing:.6px;color:var(--primary)">SETTLED</span>',
                 "": ""}[state]
        return f"""<div class="rowline" style="align-items:flex-start">
      <span class="avatar" style="background:var(--sage-cont);color:var(--sage);border-radius:50%">{person[0]}</span>
      <div style="flex:1;min-width:0">
        <div style="display:flex;align-items:center;gap:8px">
          <div class="rtitle" style="flex:1">{person}</div>{badge}
        </div>
        <div class="rmeta">{note}</div>
        <div style="display:flex;align-items:center;gap:8px;margin-top:8px">
          <div class="bar" style="flex:1;height:5px"><i style="width:{pct}%"></i></div>
          <span class="num" style="font-size:11px;font-weight:700">{left}</span>
        </div>
        <div class="rmeta" style="margin-top:6px;color:var(--ink2)">{due}</div>
      </div>
      <div class="ramt"><b class="num">{total}</b><span>original</span></div>
    </div>"""

    body = f"""
<div class="topbar">
  <div class="titles"><div class="eyebrow">Shared across accounts</div><h1>Loans &amp; debts</h1></div>
  <span class="roundbtn solid">{ICONS['plus']}</span>
</div>
<main class="content">
  <section class="card" style="padding:16px">
    <div style="display:flex;gap:10px">
      <div class="tile" style="background:var(--error-cont)">
        <div class="k" style="color:#6B1512;opacity:.7">I owe</div>
        <div class="v num neg">₪1,240</div>
      </div>
      <div class="tile" style="background:var(--primary-cont)">
        <div class="k" style="color:var(--primary-deep);opacity:.75">Owed to me</div>
        <div class="v num" style="color:var(--primary-deep)">₪2,600</div>
      </div>
    </div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-top:12px;
      padding-top:12px;border-top:.5px solid var(--line);font-size:12px">
      <span class="muted">Net position</span><b class="num pos">+₪1,360.00</b>
    </div>
  </section>

  <div class="sect"><h2>I owe · 2</h2><a href="#">+ Add</a></div>
  <section class="card tight" style="padding:4px 12px">
    {loan("Dana Levi", "Split rent · October", "₪980.00", "₪735.00 left", 25, "Due 28 Oct · made 1 Oct", "overdue")}
    {loan("Yossi", "Concert tickets", "₪505.00", "settled", 100, "Settled 12 Oct · made 2 Sep", "settled")}
  </section>

  <div class="sect"><h2>Owed to me · 3</h2><a href="#">+ Add</a></div>
  <section class="card tight" style="padding:4px 12px">
    {loan("Noa", "Weekend cabin", "₪1,200.00", "₪800.00 left", 33, "Due 2 Nov · made 14 Sep", "")}
    {loan("Amir", "Laptop repair", "₪900.00", "₪450.00 left", 50, "Due 15 Nov · made 1 Aug", "")}
    {loan("Maya", "Groceries", "₪500.00", "settled", 100, "Settled 3 Sep · made 20 Aug", "settled")}
  </section>

  <div class="sect"><h2>Reminders</h2></div>
  <section class="card tight" style="padding:12px;display:flex;gap:10px;align-items:flex-start;
    background:var(--amber-cont);border-color:#EBD3A4">
    <span style="font-size:16px">⚠</span>
    <div>
      <div style="font-size:13px;font-weight:700;color:#5C3C08">Dana Levi is overdue by 7 days</div>
      <div style="font-size:11.5px;color:#6B4406;margin-top:2px">Notifications are on for due dates at 09:00.</div>
    </div>
  </section>
  <div class="btn primary">Add loan or debt</div>
</main>
"""
    return page("Loans & debts", body, tabs=tabbar("loans"))


# ---------------------------------------------------------------- SETTINGS
def settings():
    def srow(sym, sym_bg, sym_fg, title, sub, value="", chev=True):
        return f"""<div class="rowline">
      <span class="avatar" style="background:{sym_bg};color:{sym_fg};font-size:15px">{sym}</span>
      <div style="flex:1;min-width:0"><div class="rtitle">{title}</div><div class="rmeta">{sub}</div></div>
      {f'<span class="muted num" style="font-size:12px">{value}</span>' if value else ''}
      {f'<span class="chev">›</span>' if chev else ''}
    </div>"""

    body = f"""
<div class="topbar">
  <div class="titles"><div class="eyebrow">Version 1.8.1</div><h1>Settings</h1></div>
</div>
<main class="content">
  <section class="card" style="display:flex;align-items:center;gap:12px">
    <span class="avatar" style="width:44px;height:44px;flex:0 0 44px;border-radius:14px;background:var(--primary);color:#fff">M</span>
    <div style="flex:1;min-width:0">
      <div class="rtitle" style="font-size:15px">Main account</div>
      <div class="rmeta">ILS ₪ · 3 balances · active</div>
    </div>
    <span class="pill sel">Switch</span>
  </section>

  <div class="sect"><h2>Appearance</h2></div>
  <section class="card">
    <div style="display:flex;gap:6px">
      <span class="chip" style="flex:1;justify-content:center">System</span>
      <span class="chip on" style="flex:1;justify-content:center">Light</span>
      <span class="chip" style="flex:1;justify-content:center">Dark</span>
    </div>
    <div class="rmeta" style="margin-top:10px">Amber focus ring stays visible on both themes.</div>
  </section>

  <div class="sect"><h2>Accounts</h2><a href="#">+ New</a></div>
  <section class="card tight" style="padding:0 12px">
    {srow("M", "var(--primary-cont)", "var(--primary-deep)", "Main / debit", "ILS · low balance at ₪500", "₪12,480.25")}
    {srow("C", "var(--amber-cont)", "var(--amber)", "Cash", "ILS · sub-account", "₪820.00")}
    {srow("V", "var(--error-cont)", "var(--error)", "Visa •• 4421", "ILS · autopay 28 Oct", "−₪318.15")}
  </section>

  <div class="sect"><h2>Data</h2></div>
  <section class="card tight" style="padding:0 12px">
    {srow("⇩", "var(--sage-cont)", "var(--sage)", "Backup files", "JSON, CSV or encrypted .stbk")}
    {srow("⇧", "var(--sage-cont)", "var(--sage)", "Restore", "Replace all data after confirmation")}
    {srow("⌫", "var(--error-cont)", "var(--error)", "Reset all data", "Keeps categories, clears ledger")}
  </section>

  <div class="sect"><h2>Exchange rates</h2></div>
  <section class="card tight" style="padding:0 12px">
    {srow("↻", "var(--primary-cont)", "var(--primary-deep)", "Refresh now", "open.er-api.com · 1.9 KB", "3h ago")}
    {srow("!", "var(--amber-cont)", "var(--amber)", "Stale-rate warning", "Warn when rates are older than 24h", "", False)}
  </section>

  <div class="sect"><h2>About</h2></div>
  <section class="card tight" style="padding:0 12px">
    {srow("i", "var(--sage-cont)", "var(--sage)", "Version", "Offline-first ledger", "1.8.1")}
    {srow("☰", "var(--sage-cont)", "var(--sage)", "Privacy", "No accounts, no cloud sync")}
  </section>
  <div class="empty" style="margin-top:4px">All data stays on this device.</div>
</main>
"""
    return page("Settings", body, tabs=tabbar("settings"))


# ---------------------------------------------------------------- ADD CASH DIALOG
def add_cash():
    body = """
<div class="scrim">
  <div class="dialog">
    <h2>Add cash balance</h2>
    <div class="hint">A cash balance is a separate wallet. Opening amounts do not withdraw money from main/debit.</div>
    <div class="stack">
      <div class="field"><div class="lbl">Name</div><div class="val">Cash</div></div>
      <div class="field"><div class="lbl">Opening cash</div><div class="val num">0.00</div></div>
    </div>
    <div class="dactions">
      <span class="txt">Cancel</span>
      <span class="btn primary">Create</span>
    </div>
  </div>
</div>
<div class="badge-note"><b>Both actions are always rendered.</b> The action row wraps instead of
  measuring the confirm button at zero width — this dialog previously offered only “Cancel”.</div>
"""
    return page("Add cash balance", body, tabs=tabbar("home"))


def delete_confirm():
    body = """
<div class="scrim">
  <div class="dialog">
    <h2>Edit cash</h2>
    <div class="hint">cash · ILS · opening ₪820.00</div>
    <div class="stack">
      <div class="field"><div class="lbl">Name</div><div class="val">Cash</div></div>
      <div class="field"><div class="lbl">Low balance warning (optional)</div><div class="val num">100.00</div></div>
    </div>
    <div class="hint">Deleting this balance also removes its expenses, deposits and payments.</div>
    <div class="dactions">
      <span class="txt danger">Delete</span>
      <span class="txt">Cancel</span>
      <span class="btn primary">Save</span>
    </div>
  </div>
</div>
<div class="badge-note"><b>Three actions, one row.</b> Delete asks for confirmation in a second
  dialog (“Delete transaction?” / Cancel · Delete). Destructive buttons carry the word, not just the colour.</div>
"""
    return page("Confirmations", body, tabs=tabbar("home"))


def add_menu():
    body = """
<div class="topbar">
  <div class="titles">
    <div class="eyebrow">Tue, 21 Oct</div>
    <h1>Spending</h1>
  </div>
  <span class="pill sel">Main · ₪</span>
  <span class="roundbtn">${ICONS['plus']}</span>
</div>
<main class="content">
  <section class="hero">
    <div class="glow"></div>
    <div class="eyebrow">Total balance</div>
    <div class="amount num"><small>₪</small>12,480.25</div>
    <div class="stats">
      <div><span>This week</span><b class="num">₪1,284.50</b></div>
      <div><span>This month</span><b class="num">₪4,912.80</b></div>
    </div>
    <div class="foot"><span class="tag">● Rates updated 3h ago</span><a class="adjust" href="#">Adjust</a></div>
  </section>

  <div class="addmenu">
    <div class="btn primary" style="width:100%">Close</div>
    <div style="display:flex;gap:8px;margin-top:8px">
      <div class="btn primary" style="flex:1;transform:scale(1);animation:pop .22s ease-out">Expense</div>
      <div class="btn ghost" style="flex:1;color:var(--income);animation:pop .22s .06s ease-out both">Deposit</div>
      <div class="btn ghost" style="flex:1;animation:pop .22s .12s ease-out both">Withdraw</div>
    </div>
    <div class="hint" style="margin-top:8px">Moves money from main into a cash balance, converting it
      automatically when that cash balance uses another currency.</div>
  </div>

  <section class="card">
    <div style="display:flex;align-items:baseline;gap:8px">
      <div class="eyebrow" style="flex:1">Total available</div>
      <div class="num" style="font-size:15px;font-weight:700">₪12,980.10</div>
    </div>
    <div class="rowline" style="padding-top:12px">
      <span class="avatar" style="background:var(--primary-cont);color:var(--primary-deep)">M</span>
      <div style="min-width:0"><div class="rtitle">Main / debit</div><div class="rmeta">Everyday spending</div></div>
      <div class="ramt"><b class="num">₪12,480.25</b></div>
    </div>
    <div class="rowline">
      <span class="avatar" style="background:var(--amber-cont);color:var(--amber)">C</span>
      <div style="min-width:0"><div class="rtitle">Cash</div><div class="rmeta">Cash · ILS · tap to edit</div></div>
      <div class="ramt"><b class="num">₪820.00</b></div>
    </div>
    <div style="display:flex;gap:12px;padding:0 0 8px 42px">
      <span class="txt sm">Deposit</span><span class="txt sm">Withdraw</span><span class="txt sm">Set aside</span>
    </div>
  </section>

  <div style="background:var(--card);color:var(--ink2);border:1px solid var(--line);border-radius:var(--r-md);padding:12px 14px;font-size:11.5px;line-height:17px">
    <b style="color:var(--ink)">Pop-out menu.</b> The two fixed buttons became one <code>+ Add</code> that
    expands with a staggered scale/fade (60 ms per option). Back closes it; on the 240 dp Qin
    window the grid wraps to two rows so no label is clipped. Home and History share the same
    three options, and History renders them as pills.
  </div>
</main>
"""
    return page("Add menu", body, tabs=tabbar("home"))


def withdraw():
    body = """
<div class="scrim">
  <div class="dialog">
    <h2>Withdraw to cash</h2>
    <div class="eyebrow">Main / debit</div>
    <div class="num" style="font-size:16px;font-weight:700">₪12,480.25 available</div>
    <hr style="border:0;border-top:1px solid var(--line);margin:12px 0">
    <div class="eyebrow">To</div>
    <div class="stack">
      <div class="field" style="display:flex;align-items:center;gap:8px">
        <span style="width:14px;height:14px;border-radius:50%;border:2px solid var(--primary);display:inline-block"></span>
        <div class="val" style="flex:1">Cash (ILS)</div>
      </div>
      <div class="field" style="display:flex;align-items:center;gap:8px">
        <span style="width:14px;height:14px;border-radius:50%;border:1.5px solid var(--line);display:inline-block"></span>
        <div class="val" style="flex:1">Euro wallet (EUR)</div>
      </div>
    </div>
    <div class="eyebrow">Amount in</div>
    <div style="display:flex;gap:6px;margin-bottom:8px">
      <span class="pill sel">ILS</span><span class="pill">EUR</span>
    </div>
    <div class="field"><div class="lbl">Amount (ILS)</div><div class="val num">400.00</div></div>
    <div class="hint" style="color:var(--primary)">≈ €100.00 arrives on cash · 1 ILS = 0.25 EUR</div>
    <div class="hint">Type the amount in either currency — €100 or ₪400 both work, and the
      side you typed is stored exactly.</div>
    <div class="field"><div class="lbl">Note (optional)</div><div class="val muted">ATM before the trip</div></div>
    <div class="hint">Converted at the cached rate when you confirm. This is a transfer, not
      spending, so category totals are unchanged.</div>
    <div class="dactions">
      <span class="txt">Cancel</span>
      <span class="btn primary">Withdraw</span>
    </div>
  </div>
</div>
<div class="badge-note"><b>Either currency, never a dead end.</b> The amount can be typed in ₪ or €.
  With no cash balance yet the confirm action becomes “Add cash balance”, so the dialog can never
  show Cancel alone.</div>
"""
    return page("Withdraw to cash", body, tabs=tabbar("home"))


# ---------------------------------------------------------------- GALLERY
def gallery(files):
    swatches = [("#F7F5EF", "Background"), ("#FFFFFF", "Card"), ("#0F5132", "Forest primary"),
                ("#D7ECDD", "Primary container"), ("#8A5A12", "Amber tertiary"),
                ("#B3261E", "Error"), ("#171B15", "Ink"), ("#CDD1C6", "Hairline"),
                ("#FFC400", "Hardware focus")]
    sw = "".join(f'<span class="sw"><i style="background:{c}"></i>{n}</span>' for c, n in swatches)
    cards = "".join(
        f'<div class="item"><div class="frame"><iframe src="{f}" title="{title}" loading="lazy"></iframe></div>'
        f'<h3>{title}</h3><p>{desc}</p></div>'
        for f, title, desc in files)
    return f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ledger · UI design pass</title><style>{CSS}</style></head>
<body><div class="gallery">
<header>
  <div class="eyebrow">Superdesign · design iterations</div>
  <h1>Ledger — complete app UI</h1>
  <p>Six screens of the Spending Tracker redesign. Warm paper surfaces, one forest accent,
  hairline rules and tabular money. Every screen is a single self-contained HTML file;
  the Compose implementation follows the same tokens.</p>
</header>
<div class="tokens">{sw}</div>
<div class="grid">{cards}</div>
<p class="note">Token source: <code>.superdesign/design-system.md</code> ·
Compose implementation: <code>ui/theme/Color.kt</code>, <code>ui/theme/Theme.kt</code>,
<code>ui/components/AppCards.kt</code></p>
</div></body></html>"""


SCREENS = [
    ("home", "Home", "Hero balance, quick actions, balances, recent activity, categories and a 7-day trend.", home),
    ("history", "History", "Search, filters and day-grouped rows with signed amounts and tabular figures.", history),
    ("add-expense", "New expense", "Amount-first form with currency lock, keypad, category chips and account routing.", add_expense),
    ("add-deposit", "Add deposit", "Deposit is the default wording; funding it from a cash balance is opt-in and previewed.", add_deposit),
    ("converter", "Converter", "Base and target cards, mid-market rate, 7-day sparkline and quick pairs.", converter),
    ("loans", "Loans &amp; debts", "Net position, partial-repayment progress and overdue reminders.", loans),
    ("settings", "Settings", "Accounts, appearance, backup/restore, rates and about.", settings),
    ("add-cash", "Add cash balance", "Dialog actions: Cancel and Create are both always rendered and reachable.", add_cash),
    ("dialogs", "Confirmations", "Destructive actions ask first: one-tap delete is now a two-step confirm.", delete_confirm),
    ("add-menu", "Add menu", "One + Add opens Expense / Income / Withdraw with a staggered pop-out.", add_menu),
    ("withdraw", "Withdraw to cash", "Main to cash with a live conversion preview and a way forward when no cash exists.", withdraw),
]

def main():
    files = []
    for slug, title, desc, fn in SCREENS:
        name = f"{slug}_1.html"
        (OUT / name).write_text(fn(), encoding="utf-8")
        files.append((name, title, desc))
        print("wrote", name)
    (OUT / "index.html").write_text(gallery(files), encoding="utf-8")
    print("wrote index.html")


if __name__ == "__main__":
    main()
