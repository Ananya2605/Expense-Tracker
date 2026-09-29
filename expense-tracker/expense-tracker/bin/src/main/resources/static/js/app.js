/* =========================================================
   Ledger — frontend application logic
   Talks to the Spring Boot REST API at /api/*. No external
   libraries are used anywhere (charts are hand-drawn on
   <canvas>), so the page has zero dependency on internet
   access once it's loaded from the server.
   ========================================================= */

const API_BASE = '/api';

const RING_C_SM = 2 * Math.PI * 52;
const RING_C_LG = 2 * Math.PI * 70;

const state = {
  month: new Date().toISOString().slice(0, 7),
  categories: [],
  lastSummary: null,
};

// A small hand-rolled line-icon set (24x24, stroke style) so the UI has no
// dependency on an icon font or CDN.
const ICONS = {
  utensils: '<path d="M6 3v7a2 2 0 002 2v9M6 3v7M6 3H5m3 0h1M18 3v18M18 3c-1.7 0-3 2-3 5s1.3 5 3 5"/>',
  car: '<rect x="3" y="11" width="18" height="6" rx="2"/><circle cx="7.5" cy="17.5" r="1.5"/><circle cx="16.5" cy="17.5" r="1.5"/><path d="M5 11l1.5-4h11L19 11"/>',
  home: '<path d="M4 11l8-7 8 7"/><path d="M6 10v9a1 1 0 001 1h10a1 1 0 001-1v-9"/>',
  bolt: '<path d="M13 2L4 14h6l-1 8 9-12h-6l1-8z"/>',
  film: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M8 4v16M16 4v16M3 9h5M16 9h5M3 15h5M16 15h5"/>',
  heart: '<path d="M12 20s-7-4.5-9.5-9A5.5 5.5 0 0112 5a5.5 5.5 0 019.5 6c-2.5 4.5-9.5 9-9.5 9z"/>',
  bag: '<path d="M6 8h12l1 12a2 2 0 01-2 2H7a2 2 0 01-2-2L6 8z"/><path d="M9 8V6a3 3 0 016 0v2"/>',
  book: '<path d="M4 5a2 2 0 012-2h9v16H6a2 2 0 00-2 2V5z"/><path d="M20 3v16"/>',
  plane: '<path d="M3 12l18-8-8 18-2-8-8-2z"/>',
  'piggy-bank': '<circle cx="12" cy="13" r="7"/><path d="M9 9V7a1 1 0 011-1h1M18 12h2M6 19v1M16 19v1"/>',
  tag: '<path d="M20 12l-8 8-9-9V4h7l10 10a1.5 1.5 0 010 2z"/><circle cx="8" cy="8" r="1.2" fill="currentColor" stroke="none"/>',
  target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none"/>',
  lightbulb: '<path d="M9 18h6M10 21h4M12 3a6 6 0 00-3.5 10.9c.5.4.8 1 .8 1.6v.5h5.4v-.5c0-.6.3-1.2.8-1.6A6 6 0 0012 3z"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  check: '<path d="M4 12l6 6L20 6"/>',
};

function icon(name, extraClass) {
  const inner = ICONS[name] || ICONS.tag;
  return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="' + (extraClass || '') + '">' + inner + '</svg>';
}

const CATEGORY_ICON_MAP = {
  FOOD: 'utensils', TRANSPORT: 'car', HOUSING: 'home', UTILITIES: 'bolt',
  ENTERTAINMENT: 'film', HEALTH: 'heart', SHOPPING: 'bag', EDUCATION: 'book',
  TRAVEL: 'plane', SAVINGS_INVESTMENT: 'piggy-bank',
};
function categoryIcon(cat) {
  return CATEGORY_ICON_MAP[cat] || 'tag';
}

// ---------------------------------------------------------
// Init
// ---------------------------------------------------------
document.addEventListener('DOMContentLoaded', () => {
  safeRun('init theme', initTheme);
  safeRun('init nav', initNav);
  safeRun('init month picker', initMonthPicker);
  safeRun('init modal', initModal);
  safeRun('init export button', initExportButton);
  safeRun('init budget form', initBudgetForm);

  bootstrap();

  let resizeTimer;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      if (state.lastSummary) safeRun('charts (resize)', () => renderCharts(state.lastSummary));
    }, 200);
  });
});

async function bootstrap() {
  await safeRunAsync('load categories', loadCategories);
  await safeRunAsync('refresh dashboard', refreshAll);
}

function safeRun(label, fn) {
  try {
    fn();
  } catch (err) {
    console.error('[Ledger] ' + label + ' failed:', err);
  }
}

async function safeRunAsync(label, fn) {
  try {
    await fn();
  } catch (err) {
    console.error('[Ledger] ' + label + ' failed:', err);
  }
}

// ---------------------------------------------------------
// Navigation
// ---------------------------------------------------------
function initNav() {
  document.querySelectorAll('.nav-item').forEach(btn => {
    btn.addEventListener('click', () => activateView(btn.dataset.view));
  });
  document.querySelectorAll('[data-goto]').forEach(btn => {
    btn.addEventListener('click', () => activateView(btn.dataset.goto));
  });
}

function activateView(view) {
  document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('is-active', b.dataset.view === view));
  document.querySelectorAll('.view').forEach(v => v.classList.toggle('is-active', v.id === 'view-' + view));
}

function initMonthPicker() {
  const input = document.getElementById('monthSelect');
  if (!input) return;
  input.value = state.month;
  input.addEventListener('change', () => {
    state.month = input.value;
    safeRunAsync('refresh dashboard', refreshAll);
  });
}

// ---------------------------------------------------------
// Theme (dark / light) — persisted in localStorage
// ---------------------------------------------------------
function initTheme() {
  let saved = 'dark';
  try { saved = localStorage.getItem('ledger-theme') || 'dark'; } catch (e) { /* storage unavailable */ }
  applyTheme(saved);

  const toggle = document.getElementById('themeToggle');
  if (toggle) {
    toggle.addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme');
      const next = current === 'dark' ? 'light' : 'dark';
      applyTheme(next);
      try { localStorage.setItem('ledger-theme', next); } catch (e) { /* storage unavailable */ }
    });
  }
}

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  const label = document.getElementById('themeLabel');
  if (label) label.textContent = theme === 'dark' ? 'Light mode' : 'Dark mode';
  if (state.lastSummary) safeRun('charts (theme change)', () => renderCharts(state.lastSummary));
}

// ---------------------------------------------------------
// Data loading
// ---------------------------------------------------------
async function loadCategories() {
  const res = await fetch(API_BASE + '/dashboard/categories');
  if (!res.ok) throw new Error('Could not load categories');
  state.categories = await res.json();

  const formSelect = document.getElementById('formCategory');
  if (formSelect) {
    formSelect.innerHTML = state.categories.map(c => '<option value="' + c + '">' + toDisplayName(c) + '</option>').join('');
  }

  const overallInput = document.getElementById('overallBudgetInput');
  if (overallInput) {
    overallInput.addEventListener('input', () => {
      safeRun('smart split', () => renderSmartSplit(parseFloat(overallInput.value) || 0));
    });
  }
  safeRun('smart split (initial)', () => renderSmartSplit(0));
}

async function fetchJson(url, options) {
  const res = await fetch(url, options);
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      if (body && body.error) message = body.error;
    } catch (e) { /* body wasn't JSON */ }
    throw new Error(message || 'Request failed');
  }
  if (res.status === 204) return null;
  return res.json();
}

async function refreshAll() {
  const monthSelect = document.getElementById('monthSelect');
  if (monthSelect) monthSelect.value = state.month;
  const heading = document.getElementById('monthHeading');
  if (heading) heading.textContent = formatMonthHeading(state.month);

  const prevMonth = shiftMonth(state.month, -1);

  const results = await Promise.allSettled([
    fetchJson(API_BASE + '/dashboard/summary?month=' + state.month),
    fetchJson(API_BASE + '/dashboard/ai-advice?month=' + state.month),
    fetchJson(API_BASE + '/expenses?month=' + state.month),
    fetchJson(API_BASE + '/budgets?month=' + state.month),
    fetchJson(API_BASE + '/dashboard/summary?month=' + prevMonth),
  ]);

  results.forEach((r, i) => {
    if (r.status === 'rejected') {
      console.error('[Ledger] API call failed:', ['summary', 'ai-advice', 'expenses', 'budgets', 'prev-summary'][i], r.reason);
    }
  });

  const emptySummary = {
    yearMonth: state.month, totalSpent: 0, overallBudget: 0, remaining: 0,
    percentUsed: 0, projectedMonthEndSpend: 0, daysElapsed: 1, daysInMonth: 30,
    categorySummaries: [], dailySpend: {},
  };
  const emptyAdvice = { status: 'NO_BUDGET', headline: 'Could not load AI advice right now — check the server is running.', suggestions: [], healthScore: 0 };

  const summary = results[0].status === 'fulfilled' && results[0].value ? results[0].value : emptySummary;
  const advice = results[1].status === 'fulfilled' && results[1].value ? results[1].value : emptyAdvice;
  const expenses = results[2].status === 'fulfilled' && results[2].value ? results[2].value : [];
  const budgets = results[3].status === 'fulfilled' && results[3].value ? results[3].value : [];
  const prevSummary = results[4].status === 'fulfilled' ? results[4].value : null;

  if (results.some(r => r.status === 'rejected')) {
    showToast('Some data failed to load from the server — check it is running, and see the browser console.');
  }

  state.lastSummary = summary;

  safeRun('stats', () => renderStats(summary, prevSummary));
  safeRun('highlights', () => renderHighlights(summary));
  safeRun('insight strip', () => renderInsightStrip(summary, expenses, prevSummary));
  safeRun('AI panel', () => renderAiPanel(advice));
  safeRun('AI recommendations', () => renderRecommendations(advice));
  safeRun('charts', () => renderCharts(summary));
  safeRun('heatmap', () => renderHeatmap(summary));
  safeRun('category breakdown', () => renderCategoryList(summary));
  safeRun('transactions table', () => renderExpenseTable(expenses));
  safeRun('budget inputs', () => prefillBudgetInputs(budgets, summary));
}

// ---------------------------------------------------------
// Number count-up animation helper
// ---------------------------------------------------------
function animateNumber(el, from, to, formatter, duration) {
  if (!el) return;
  duration = duration || 700;
  const start = performance.now();
  const diff = to - from;
  function tick(now) {
    const t = Math.min(1, (now - start) / duration);
    const eased = 1 - Math.pow(1 - t, 3);
    el.textContent = formatter(from + diff * eased);
    if (t < 1) requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
}

// ---------------------------------------------------------
// Rendering: stats
// ---------------------------------------------------------
function renderStats(s, prevSummary) {
  animateNumber(document.getElementById('statTotalSpent'), 0, s.totalSpent, formatMoney);
  const budgetOf = document.getElementById('statBudgetOf');
  if (budgetOf) budgetOf.textContent = 'of ' + formatMoney(s.overallBudget) + ' budget';
  animateNumber(document.getElementById('statRemaining'), 0, s.remaining, formatMoney);

  const pctEl = document.getElementById('statPercentUsed');
  if (pctEl) pctEl.textContent = Math.round(s.percentUsed) + '%';
  animateNumber(document.getElementById('statProjected'), 0, s.projectedMonthEndSpend, formatMoney);

  const bar = document.getElementById('statPercentBar');
  if (bar) {
    const pct = Math.min(Math.max(s.percentUsed, 0), 100);
    requestAnimationFrame(() => { bar.style.width = pct + '%'; });
    bar.style.background = s.percentUsed >= 100 ? 'var(--danger)' : (s.percentUsed >= 80 ? 'var(--gold)' : 'var(--teal)');
  }

  const badge = document.getElementById('momBadge');
  if (badge) {
    if (prevSummary && prevSummary.totalSpent > 0) {
      const change = ((s.projectedMonthEndSpend - prevSummary.totalSpent) / prevSummary.totalSpent) * 100;
      const up = change >= 0;
      badge.textContent = (up ? '↑ ' : '↓ ') + Math.abs(change).toFixed(0) + '% vs last month';
      badge.className = 'stat-hero-badge ' + (up ? 'up' : 'down');
    } else {
      badge.textContent = 'No prior data';
      badge.className = 'stat-hero-badge';
    }
  }
}

function renderInsightStrip(summary, expenses, prevSummary) {
  const el = document.getElementById('insightStrip');
  if (!el) return;

  const avgDaily = summary.daysElapsed > 0 ? summary.totalSpent / summary.daysElapsed : 0;
  let biggest = null;
  (expenses || []).forEach(e => { if (!biggest || e.amount > biggest.amount) biggest = e; });
  const topCategory = (summary.categorySummaries || [])[0];

  const chips = [
    { label: 'Avg daily spend', value: formatMoney(avgDaily), sub: 'over ' + summary.daysElapsed + ' day' + (summary.daysElapsed === 1 ? '' : 's') },
    { label: 'Biggest expense', value: biggest ? formatMoney(biggest.amount) : '—', sub: biggest ? biggest.title : 'No transactions yet' },
    { label: 'Top category', value: topCategory ? toDisplayName(topCategory.category) : '—', sub: topCategory ? formatMoney(topCategory.spent) + ' spent' : 'No data yet' },
    { label: 'Last month total', value: prevSummary && prevSummary.totalSpent > 0 ? formatMoney(prevSummary.totalSpent) : '—', sub: 'for comparison' },
  ];

  el.innerHTML = chips.map((c, i) =>
    '<div class="insight-chip anim-in" style="--delay:' + i + '">' +
      '<span class="insight-chip-label">' + escapeHtml(c.label) + '</span>' +
      '<span class="insight-chip-value">' + escapeHtml(String(c.value)) + '</span>' +
      '<span class="insight-chip-sub">' + escapeHtml(c.sub) + '</span>' +
    '</div>'
  ).join('');
}

function renderHighlights(summary) {
  const el = document.getElementById('highlightStrip');
  if (!el) return;

  const daysLeftInclusive = Math.max(summary.daysInMonth - summary.daysElapsed + 1, 1);
  const remaining = summary.overallBudget - summary.totalSpent;
  const safeToday = summary.overallBudget > 0 ? remaining / daysLeftInclusive : null;
  const isOver = safeToday !== null && safeToday <= 0;

  const values = Object.values(summary.dailySpend || {});
  const fairShare = summary.overallBudget > 0 ? summary.overallBudget / summary.daysInMonth : null;
  let streak = 0;
  if (fairShare !== null) {
    for (let i = values.length - 1; i >= 0; i--) {
      if (values[i] <= fairShare) streak++; else break;
    }
  }

  el.innerHTML =
    '<div class="highlight-card safe-to-spend anim-in ' + (isOver ? 'is-over' : '') + '" style="--delay:0">' +
      '<span class="highlight-icon">' + icon('target') + '</span>' +
      '<div>' +
        '<div class="highlight-label">Safe to spend today</div>' +
        '<div class="highlight-value">' + (safeToday === null ? 'Set a budget' : formatMoney(Math.max(safeToday, 0))) + '</div>' +
        '<div class="highlight-sub">' + (safeToday === null ? 'Add an overall budget to unlock this' : (isOver ? "You're over pace for the month" : 'Based on ' + daysLeftInclusive + ' day' + (daysLeftInclusive === 1 ? '' : 's') + ' left')) + '</div>' +
      '</div>' +
    '</div>' +
    '<div class="highlight-card streak-card anim-in" style="--delay:1">' +
      '<div class="streak-flames">' + ('🔥'.repeat(Math.min(Math.max(streak, 0), 5)) || '—') + '</div>' +
      '<div class="streak-count">' + streak + ' day' + (streak === 1 ? '' : 's') + ' on pace</div>' +
      '<div class="highlight-sub">Days this month you spent at or below your daily fair share</div>' +
    '</div>';
}

// ---------------------------------------------------------
// Rendering: AI advisor
// ---------------------------------------------------------
function ringColor(score) {
  if (score >= 70) return 'var(--teal)';
  if (score >= 40) return 'var(--gold)';
  return 'var(--danger)';
}

function setRing(fillEl, valueEl, score, circumference) {
  if (!fillEl) return;
  const clamped = Math.max(0, Math.min(100, score || 0));
  const offset = circumference - (clamped / 100) * circumference;
  fillEl.style.strokeDasharray = circumference + ' ' + circumference;
  requestAnimationFrame(() => { fillEl.style.strokeDashoffset = offset; });
  fillEl.style.stroke = ringColor(clamped);
  if (valueEl) valueEl.textContent = clamped;
}

function renderAiPanel(advice) {
  const panel = document.getElementById('aiPanel');
  if (panel) panel.classList.toggle('status-over', advice.status === 'OVER_BUDGET');

  const headline = document.getElementById('aiHeadline');
  if (headline) headline.textContent = advice.headline || '';
  const headlineFull = document.getElementById('aiHeadlineFull');
  if (headlineFull) headlineFull.textContent = advice.headline || '';

  setRing(document.getElementById('healthRingFill'), document.getElementById('healthScoreValue'), advice.healthScore, RING_C_SM);
  setRing(document.getElementById('healthRingFillLg'), document.getElementById('healthScoreValueLg'), advice.healthScore, RING_C_LG);

  const preview = document.getElementById('aiSuggestionsPreview');
  if (preview) {
    const top3 = (advice.suggestions || []).slice(0, 3);
    preview.innerHTML = top3.map(s =>
      '<div class="ai-suggestion-mini"><span class="dot ' + (s.priority || 'low') + '"></span><span>' + escapeHtml(s.title || '') + '</span></div>'
    ).join('');
  }
}

function renderRecommendations(advice) {
  const grid = document.getElementById('recommendationGrid');
  if (!grid) return;
  const suggestions = advice.suggestions || [];
  if (!suggestions.length) {
    grid.innerHTML = '<p class="empty-state">No recommendations yet.</p>';
    return;
  }
  grid.innerHTML = suggestions.map((s, i) =>
    '<div class="rec-card priority-' + (s.priority || 'low') + '" style="animation-delay:' + (i * 50) + 'ms">' +
      '<div class="rec-card-top">' +
        '<span class="rec-icon">' + icon(s.icon) + '</span>' +
        '<span class="priority-tag ' + (s.priority || 'low') + '">' + (s.priority || 'low') + '</span>' +
      '</div>' +
      '<h4 class="rec-title">' + escapeHtml(s.title || '') + '</h4>' +
      '<p class="rec-detail">' + escapeHtml(s.detail || '') + '</p>' +
      (s.impactAmount ? '<div class="rec-impact">' + icon('bolt', 'btn-icon') + ' Potential impact: ' + formatMoney(s.impactAmount) + '</div>' : '') +
    '</div>'
  ).join('');
}

// ---------------------------------------------------------
// Rendering: category list
// ---------------------------------------------------------
function renderCategoryList(s) {
  const container = document.getElementById('categoryList');
  if (!container) return;
  const cats = s.categorySummaries || [];
  if (!cats.length) {
    container.innerHTML = '<p class="empty-state">No spending or budget data for this month yet.</p>';
    return;
  }
  container.innerHTML = cats.map(cs => {
    const pct = Math.min(cs.percentUsed || 0, 100);
    const over = (cs.percentUsed || 0) > 100;
    return (
      '<div class="category-row">' +
        '<span class="category-row-icon">' + icon(categoryIcon(cs.category)) + '</span>' +
        '<span class="category-name">' + toDisplayName(cs.category) + '</span>' +
        '<div class="category-bar-track"><div class="category-bar-fill ' + (over ? 'over' : '') + '" data-width="' + pct + '"></div></div>' +
        '<span class="category-figures">' + formatMoney(cs.spent) + (cs.limit > 0 ? ' / ' + formatMoney(cs.limit) : '') + '</span>' +
      '</div>'
    );
  }).join('');

  requestAnimationFrame(() => {
    container.querySelectorAll('.category-bar-fill').forEach(el => {
      el.style.width = el.dataset.width + '%';
    });
  });
}

// ---------------------------------------------------------
// Rendering: heatmap calendar
// ---------------------------------------------------------
function renderHeatmap(summary) {
  const grid = document.getElementById('heatmapGrid');
  if (!grid) return;
  const entries = Object.entries(summary.dailySpend || {});
  if (!entries.length) { grid.innerHTML = '<p class="empty-state">No data yet.</p>'; return; }

  const parts = (summary.yearMonth || '').split('-').map(Number);
  const year = parts[0], month = parts[1];
  if (!year || !month) { grid.innerHTML = ''; return; }

  const firstWeekday = new Date(year, month - 1, 1).getDay();
  const max = Math.max.apply(null, entries.map(e => e[1]).concat([1]));

  let html = '';
  for (let i = 0; i < firstWeekday; i++) html += '<div class="heatmap-cell empty"></div>';

  entries.forEach((entry, idx) => {
    const label = entry[0], amount = entry[1];
    const ratio = amount / max;
    let level = 'l0';
    if (amount > 0) {
      if (ratio > 0.75) level = 'l4';
      else if (ratio > 0.5) level = 'l3';
      else if (ratio > 0.25) level = 'l2';
      else level = 'l1';
    }
    html += '<div class="heatmap-cell ' + level + '" data-tip="' + escapeHtml(label + ': ' + formatMoney(amount)) + '">' + (idx + 1) + '</div>';
  });

  grid.innerHTML = html;
}

// ---------------------------------------------------------
// Rendering: charts (hand-drawn on canvas, no libraries)
// ---------------------------------------------------------
function chartPalette() {
  const dark = document.documentElement.getAttribute('data-theme') === 'dark';
  return {
    grid: dark ? 'rgba(255,255,255,0.07)' : 'rgba(20,24,32,0.08)',
    text: dark ? '#8D97AA' : '#5B6472',
    textStrong: dark ? '#EDF0F5' : '#171B24',
    accent: '#1D9A8C',
    accentSoftTop: dark ? 'rgba(29,154,140,0.30)' : 'rgba(29,154,140,0.22)',
    accentSoftBottom: 'rgba(29,154,140,0)',
    series: ['#1D9A8C', '#7C6FE0', '#C4922E', '#D6455D', '#4C8FD1', '#3AA66B', '#C7628F', '#B98A2E', '#6E7FD4', '#4FA871', '#C15D5D'],
  };
}

function setupCanvasResolution(canvas, cssHeight) {
  const dpr = window.devicePixelRatio || 1;
  const parent = canvas.parentElement;
  const cssWidth = parent ? parent.clientWidth : 300;
  canvas.style.width = cssWidth + 'px';
  canvas.style.height = cssHeight + 'px';
  canvas.width = Math.max(1, Math.round(cssWidth * dpr));
  canvas.height = Math.max(1, Math.round(cssHeight * dpr));
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  return { ctx: ctx, width: cssWidth, height: cssHeight };
}

function chartTooltip() {
  let el = document.getElementById('chartTooltip');
  if (!el) {
    el = document.createElement('div');
    el.id = 'chartTooltip';
    el.className = 'chart-tooltip';
    document.body.appendChild(el);
  }
  return el;
}
function showChartTooltip(clientX, clientY, html) {
  const el = chartTooltip();
  el.innerHTML = html;
  el.style.left = (clientX + 12) + 'px';
  el.style.top = (clientY - 14) + 'px';
  el.classList.add('is-visible');
}
function hideChartTooltip() {
  const el = document.getElementById('chartTooltip');
  if (el) el.classList.remove('is-visible');
}

function drawLineChart(canvasId, labels, values) {
  const canvas = document.getElementById(canvasId);
  if (!canvas || !canvas.parentElement) return;
  const colors = chartPalette();
  const dims = setupCanvasResolution(canvas, 230);
  const ctx = dims.ctx, width = dims.width, height = dims.height;

  ctx.clearRect(0, 0, width, height);

  const padL = 46, padR = 12, padT = 14, padB = 24;
  const plotW = Math.max(1, width - padL - padR);
  const plotH = Math.max(1, height - padT - padB);
  const maxVal = Math.max.apply(null, values.concat([1])) * 1.15;

  ctx.strokeStyle = colors.grid;
  ctx.fillStyle = colors.text;
  ctx.font = '11px Inter, sans-serif';
  ctx.textAlign = 'right';
  ctx.textBaseline = 'middle';
  const gridLines = 4;
  for (let i = 0; i <= gridLines; i++) {
    const y = padT + (plotH / gridLines) * i;
    ctx.beginPath();
    ctx.moveTo(padL, y);
    ctx.lineTo(width - padR, y);
    ctx.lineWidth = 1;
    ctx.stroke();
    const val = maxVal - (maxVal / gridLines) * i;
    ctx.fillText(val >= 1000 ? Math.round(val / 1000) + 'k' : Math.round(val).toString(), padL - 8, y);
  }

  if (!values.length) {
    ctx.textAlign = 'center';
    ctx.fillText('No spending recorded yet', width / 2, height / 2);
    return;
  }

  const stepX = values.length > 1 ? plotW / (values.length - 1) : 0;
  const points = values.map((v, i) => ({ x: padL + stepX * i, y: padT + plotH - (v / maxVal) * plotH, label: labels[i], value: v }));

  const gradient = ctx.createLinearGradient(0, padT, 0, padT + plotH);
  gradient.addColorStop(0, colors.accentSoftTop);
  gradient.addColorStop(1, colors.accentSoftBottom);
  ctx.beginPath();
  ctx.moveTo(points[0].x, padT + plotH);
  points.forEach(p => ctx.lineTo(p.x, p.y));
  ctx.lineTo(points[points.length - 1].x, padT + plotH);
  ctx.closePath();
  ctx.fillStyle = gradient;
  ctx.fill();

  ctx.beginPath();
  points.forEach((p, i) => { if (i === 0) ctx.moveTo(p.x, p.y); else ctx.lineTo(p.x, p.y); });
  ctx.strokeStyle = colors.accent;
  ctx.lineWidth = 2.5;
  ctx.lineJoin = 'round';
  ctx.stroke();

  ctx.fillStyle = colors.accent;
  points.forEach(p => { ctx.beginPath(); ctx.arc(p.x, p.y, 2.4, 0, Math.PI * 2); ctx.fill(); });

  const maxLabels = Math.max(1, Math.floor(plotW / 46));
  const labelEvery = Math.max(1, Math.ceil(points.length / maxLabels));
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  points.forEach((p, i) => {
    if (i % labelEvery === 0 || i === points.length - 1) ctx.fillText(p.label, p.x, height - padB + 6);
  });

  canvas.onmousemove = function (e) {
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    let nearest = points[0], minDist = Infinity;
    points.forEach(p => { const d = Math.abs(p.x - mouseX); if (d < minDist) { minDist = d; nearest = p; } });
    showChartTooltip(e.clientX, e.clientY, '<strong>' + escapeHtml(nearest.label) + '</strong><br>' + formatMoney(nearest.value));
  };
  canvas.onmouseleave = hideChartTooltip;
}

function drawDoughnutChart(canvasId, legendId, labels, values) {
  const canvas = document.getElementById(canvasId);
  const legendEl = document.getElementById(legendId);
  if (!canvas || !canvas.parentElement) return;
  const colors = chartPalette();
  const dims = setupCanvasResolution(canvas, 230);
  const ctx = dims.ctx, width = dims.width, height = dims.height;

  ctx.clearRect(0, 0, width, height);

  const total = values.reduce((a, b) => a + b, 0);
  if (!total) {
    ctx.fillStyle = colors.text;
    ctx.font = '13px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('No spending recorded yet', width / 2, height / 2);
    if (legendEl) legendEl.innerHTML = '';
    return;
  }

  const cx = width / 2, cy = height / 2;
  const outerR = Math.min(cx, cy) - 8;
  const innerR = outerR * 0.6;
  let angle = -Math.PI / 2;
  const segments = [];

  values.forEach((v, i) => {
    const slice = (v / total) * Math.PI * 2;
    const color = colors.series[i % colors.series.length];
    ctx.beginPath();
    ctx.arc(cx, cy, outerR, angle, angle + slice);
    ctx.arc(cx, cy, innerR, angle + slice, angle, true);
    ctx.closePath();
    ctx.fillStyle = color;
    ctx.fill();
    segments.push({ start: angle, end: angle + slice, color: color, label: labels[i], value: v });
    angle += slice;
  });

  ctx.fillStyle = colors.textStrong;
  ctx.font = '700 15px "IBM Plex Mono", monospace';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(formatMoney(total), cx, cy - 6);
  ctx.fillStyle = colors.text;
  ctx.font = '11px Inter, sans-serif';
  ctx.fillText('total', cx, cy + 12);

  if (legendEl) {
    legendEl.innerHTML = segments.map(s =>
      '<div class="chart-legend-item">' +
        '<span class="chart-legend-dot" style="background:' + s.color + '"></span>' +
        '<span class="chart-legend-label">' + escapeHtml(s.label) + '</span>' +
        '<span class="chart-legend-value">' + Math.round((s.value / total) * 100) + '%</span>' +
      '</div>'
    ).join('');
  }

  canvas.onmousemove = function (e) {
    const rect = canvas.getBoundingClientRect();
    const mx = e.clientX - rect.left - cx, my = e.clientY - rect.top - cy;
    const dist = Math.sqrt(mx * mx + my * my);
    if (dist < innerR || dist > outerR) { hideChartTooltip(); return; }
    let a = Math.atan2(my, mx);
    if (a < -Math.PI / 2) a += Math.PI * 2;
    const hit = segments.filter(s => a >= s.start && a <= s.end)[0];
    if (hit) {
      showChartTooltip(e.clientX, e.clientY, '<strong>' + escapeHtml(hit.label) + '</strong><br>' + formatMoney(hit.value) + ' (' + Math.round((hit.value / total) * 100) + '%)');
    }
  };
  canvas.onmouseleave = hideChartTooltip;
}

function renderCharts(summary) {
  if (!summary) return;
  const trendLabels = Object.keys(summary.dailySpend || {});
  const trendValues = Object.values(summary.dailySpend || {});
  drawLineChart('trendChart', trendLabels, trendValues);

  const cats = (summary.categorySummaries || []).filter(c => c.spent > 0);
  drawDoughnutChart('categoryChart', 'categoryChartLegend', cats.map(c => toDisplayName(c.category)), cats.map(c => c.spent));
}

// ---------------------------------------------------------
// Rendering: expense table
// ---------------------------------------------------------
function renderExpenseTable(expenses) {
  const tbody = document.getElementById('expenseTableBody');
  const emptyState = document.getElementById('expenseEmptyState');
  if (!tbody) return;
  const list = expenses || [];
  if (!list.length) {
    tbody.innerHTML = '';
    if (emptyState) emptyState.hidden = false;
    return;
  }
  if (emptyState) emptyState.hidden = true;
  tbody.innerHTML = list.map(e =>
    '<tr>' +
      '<td>' + escapeHtml(e.date || '') + '</td>' +
      '<td>' + escapeHtml(e.title || '') + '</td>' +
      '<td><span class="category-pill">' + toDisplayName(e.category) + '</span></td>' +
      '<td>' + toDisplayName(e.paymentMethod) + '</td>' +
      '<td class="num amount">' + formatMoney(e.amount) + '</td>' +
      '<td><button class="row-delete" data-id="' + e.id + '" type="button">Delete</button></td>' +
    '</tr>'
  ).join('');

  tbody.querySelectorAll('.row-delete').forEach(btn => {
    btn.addEventListener('click', () => deleteExpense(btn.dataset.id));
  });
}

async function deleteExpense(id) {
  if (!confirm('Delete this expense?')) return;
  try {
    await fetchJson(API_BASE + '/expenses/' + id, { method: 'DELETE' });
    showToast('Expense deleted');
    await safeRunAsync('refresh dashboard', refreshAll);
  } catch (err) {
    showToast(err.message || 'Could not delete expense');
  }
}

// ---------------------------------------------------------
// Add expense modal
// ---------------------------------------------------------
function initModal() {
  const backdrop = document.getElementById('modalBackdrop');
  const openBtn = document.getElementById('addExpenseBtn');
  const cancelBtn = document.getElementById('cancelExpenseBtn');
  const form = document.getElementById('expenseForm');
  if (!backdrop || !form) return;

  if (openBtn) {
    openBtn.addEventListener('click', () => {
      const dateField = document.getElementById('formDate');
      if (dateField) dateField.value = new Date().toISOString().slice(0, 10);
      backdrop.classList.add('is-open');
    });
  }
  if (cancelBtn) cancelBtn.addEventListener('click', () => backdrop.classList.remove('is-open'));
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) backdrop.classList.remove('is-open'); });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
      title: document.getElementById('formTitle').value,
      amount: parseFloat(document.getElementById('formAmount').value),
      date: document.getElementById('formDate').value,
      category: document.getElementById('formCategory').value,
      paymentMethod: document.getElementById('formPaymentMethod').value,
      notes: document.getElementById('formNotes').value,
    };
    try {
      await fetchJson(API_BASE + '/expenses', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      backdrop.classList.remove('is-open');
      form.reset();
      showToast('Expense added');
      state.month = payload.date.slice(0, 7);
      await safeRunAsync('refresh dashboard', refreshAll);
    } catch (err) {
      showToast(err.message || 'Could not save expense');
    }
  });
}

// ---------------------------------------------------------
// Budget saving
// ---------------------------------------------------------
function initBudgetForm() {
  const btn = document.getElementById('saveOverallBudgetBtn');
  if (!btn) return;
  btn.addEventListener('click', async () => {
    const input = document.getElementById('overallBudgetInput');
    const amount = parseFloat(input.value);
    if (!amount || amount <= 0) { showToast('Enter a valid budget amount'); return; }
    try {
      await fetchJson(API_BASE + '/budgets/overall', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ yearMonth: state.month, amount: amount }),
      });
      showToast('Overall budget saved');
      await safeRunAsync('refresh dashboard', refreshAll);
    } catch (err) {
      showToast(err.message || 'Could not save budget');
    }
  });
}

async function saveCategoryBudget(category) {
  const input = document.getElementById('budget-' + category);
  if (!input) return;
  const amount = parseFloat(input.value);
  if (!amount || amount <= 0) { showToast('Enter a valid limit'); return; }
  try {
    await fetchJson(API_BASE + '/budgets/category', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ yearMonth: state.month, category: category, amount: amount }),
    });
    showToast(toDisplayName(category) + ' budget saved');
    await safeRunAsync('refresh dashboard', refreshAll);
  } catch (err) {
    showToast(err.message || 'Could not save budget');
  }
}

function prefillBudgetInputs(budgets, summary) {
  const list = budgets || [];
  const overall = list.filter(b => !b.category)[0];
  const overallInput = document.getElementById('overallBudgetInput');
  if (overallInput) overallInput.value = overall ? overall.limitAmount : '';
  renderSmartSplit(overall ? overall.limitAmount : 0);

  const limitByCategory = {};
  list.forEach(b => { if (b.category) limitByCategory[b.category] = b.limitAmount; });
  const spentByCategory = {};
  (summary.categorySummaries || []).forEach(cs => { spentByCategory[cs.category] = cs.spent; });

  const grid = document.getElementById('categoryBudgetGrid');
  if (!grid) return;
  grid.innerHTML = state.categories.map(c => {
    const limit = limitByCategory[c] || 0;
    const spent = spentByCategory[c] || 0;
    const pct = limit > 0 ? Math.min((spent / limit) * 100, 100) : 0;
    const over = limit > 0 && spent > limit;
    return (
      '<div class="category-budget-item">' +
        '<div class="envelope-top">' +
          '<span class="envelope-icon">' + icon(categoryIcon(c)) + '</span>' +
          '<label>' + toDisplayName(c) + '</label>' +
        '</div>' +
        (limit > 0
          ? '<div class="category-bar-track"><div class="category-bar-fill ' + (over ? 'over' : '') + '" style="width:' + pct + '%"></div></div>' +
            '<span class="envelope-figures">' + formatMoney(spent) + ' of ' + formatMoney(limit) + '</span>'
          : '<span class="envelope-figures">No limit set yet</span>') +
        '<div class="inline-row">' +
          '<input type="number" min="0" step="0.01" placeholder="₹ limit" id="budget-' + c + '" value="' + (limit || '') + '">' +
          '<button data-category="' + c + '" class="save-category-budget" type="button">Save</button>' +
        '</div>' +
      '</div>'
    );
  }).join('');

  grid.querySelectorAll('.save-category-budget').forEach(btn => {
    btn.addEventListener('click', () => saveCategoryBudget(btn.dataset.category));
  });
}

// ---------------------------------------------------------
// Smart Split (50/30/20 rule)
// ---------------------------------------------------------
function renderSmartSplit(overallBudget) {
  const el = document.getElementById('smartSplitGrid');
  if (!el) return;
  const needs = overallBudget * 0.5;
  const wants = overallBudget * 0.3;
  const savings = overallBudget * 0.2;
  const rows = [
    { key: 'needs', label: 'Needs', pct: '50%', value: needs, hint: 'Rent, groceries, utilities, transport, tuition — the non-negotiables.' },
    { key: 'wants', label: 'Wants', pct: '30%', value: wants, hint: 'Eating out, entertainment, shopping, subscriptions — nice to have.' },
    { key: 'savings', label: 'Savings', pct: '20%', value: savings, hint: 'Emergency fund, investments, or that big purchase you are saving for.' },
  ];
  el.innerHTML = rows.map(r =>
    '<div class="split-card ' + r.key + '">' +
      '<div class="split-card-label">' + r.label + ' <span class="split-card-pct">' + r.pct + '</span></div>' +
      '<div class="split-card-value">' + (overallBudget > 0 ? formatMoney(r.value) : '—') + '</div>' +
      '<div class="split-card-hint">' + r.hint + '</div>' +
    '</div>'
  ).join('');
}

// ---------------------------------------------------------
// Excel export
// ---------------------------------------------------------
function initExportButton() {
  const btn = document.getElementById('exportExcelBtn');
  if (!btn) return;
  btn.addEventListener('click', () => {
    window.location.href = API_BASE + '/export/excel?month=' + state.month;
    showToast('Preparing your Excel report…');
  });
}

// ---------------------------------------------------------
// Helpers
// ---------------------------------------------------------
function formatMoney(v) {
  const n = Number(v) || 0;
  return '₹' + n.toLocaleString('en-IN', { maximumFractionDigits: 0 });
}

function toDisplayName(enumValue) {
  if (!enumValue) return '';
  return String(enumValue).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}

function formatMonthHeading(yyyyMM) {
  const parts = yyyyMM.split('-');
  const date = new Date(Number(parts[0]), Number(parts[1]) - 1, 1);
  return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
}

function shiftMonth(yyyyMM, delta) {
  const parts = yyyyMM.split('-').map(Number);
  const date = new Date(parts[0], parts[1] - 1 + delta, 1);
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0');
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str == null ? '' : String(str);
  return div.innerHTML;
}

let toastTimer;
function showToast(message) {
  const toast = document.getElementById('toast');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('is-visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('is-visible'), 2800);
}
