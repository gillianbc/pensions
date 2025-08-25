// Money utils (integers in pence to avoid floating errors)
const toPence = (str) => {
  if (typeof str === 'number') return Math.round(str * 100);
  if (!str) return 0;
  const cleaned = String(str).replace(/[^0-9.\-]/g, '');
  const num = Number(cleaned);
  if (Number.isNaN(num)) return 0;
  return Math.round(num * 100);
};
const fromPence = (p) => (p / 100);
const formatGBP = (p) => {
  const n = fromPence(p);
  return '£' + n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};
const clampNonNeg = (p) => (p < 0 ? 0 : p);

// Rate helpers
const mulRatePence = (pence, rate) => Math.round(pence * rate);
const addRate = (rate) => 1 + rate;

// Data structure for a year snapshot
const wealth = (age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaid, extraThisYear) => ({
  age, pensionStart, pensionEnd, savingsStart, savingsEnd, taxPaid, extraThisYear,
  totalEnd() { return this.pensionEnd + this.savingsEnd; }
});

// Parse ad hoc field: "62:5000; 70:10000"
const parseAdhoc = (text) => {
  const map = {};
  if (!text || !text.trim()) return map;
  const pairs = text.split(';').map(s => s.trim()).filter(Boolean);
  for (const pair of pairs) {
    const [a, v] = pair.split(':').map(s => s.trim());
    const age = Number(a);
    const val = toPence(v);
    if (!Number.isNaN(age) && age > 0 && val >= 0) map[age] = val;
  }
  return map;
};

// Strategies
function strategy1(savingsP, pensionP, requiredNetP, adhoc, params) {
  const { START_AGE, END_AGE, STATE_PENSION_P, PERSONAL_ALLOWANCE_P, BASIC_RATE, PENSION_GROWTH_RATE } = params;
  const len = END_AGE - START_AGE + 1;
  const timeline = new Array(len);
  let lumpSumTaken = false;

  let age = START_AGE;
  for (let idx = 0; idx < len; idx++, age++) {
    const pensionStart = Math.round(pensionP);
    const savingsStart = Math.round(savingsP);
    let taxPaid = 0;

    const statePensionIncome = age >= 67 ? STATE_PENSION_P : 0;
    const extra = adhoc[age] || 0;

    let need = requiredNetP + extra - statePensionIncome;
    if (need < 0) need = 0;

    // Use savings first
    let fromSavings = Math.min(need, savingsP);
    savingsP -= fromSavings;
    need -= fromSavings;

    // One-time lump sum: 25% of pension into savings if still needed
    if (need > 0 && !lumpSumTaken && pensionP > 0) {
      const lump = Math.round(pensionP * 0.25);
      pensionP -= lump;
      savingsP += lump;
      lumpSumTaken = true;

      const fromSavings2 = Math.min(need, savingsP);
      savingsP -= fromSavings2;
      need -= fromSavings2;
    }

    // If still needed, withdraw from pension applying tax rules
    if (need > 0 && pensionP > 0) {
      let allowanceLeft = PERSONAL_ALLOWANCE_P - statePensionIncome;
      if (allowanceLeft < 0) allowanceLeft = 0;

      // If within allowance: gross = need; else split across allowance and taxed-at-basic
      let grossRequired;
      if (need <= allowanceLeft) {
        grossRequired = need;
      } else {
        const remainingNet = need - allowanceLeft;
        const grossAbove = Math.round(remainingNet / (1 - BASIC_RATE));
        grossRequired = allowanceLeft + grossAbove;
      }
      const grossWithdraw = Math.min(grossRequired, pensionP);

      const zeroTaxPortion = Math.min(grossWithdraw, allowanceLeft);
      let basicTaxPortion = grossWithdraw - zeroTaxPortion;
      if (basicTaxPortion < 0) basicTaxPortion = 0;

      const netFromPension = zeroTaxPortion + Math.round(basicTaxPortion * (1 - BASIC_RATE));
      const taxForThisWithdrawal = Math.round(basicTaxPortion * BASIC_RATE);
      taxPaid += taxForThisWithdrawal;

      need -= netFromPension;
      if (need < 0) need = 0;

      pensionP -= grossWithdraw;
    }

    // End-of-year growth
    pensionP = Math.round(pensionP * addRate(PENSION_GROWTH_RATE));

    const pensionEnd = Math.round(pensionP);
    const savingsEnd = Math.round(savingsP);
    timeline[idx] = wealth(age, pensionStart, pensionEnd, savingsStart, savingsEnd, Math.round(taxPaid), Math.round(extra));
  }
  return timeline;
}

function strategy2(savingsP, pensionP, requiredNetP, adhoc, params) {
  const { START_AGE, END_AGE, STATE_PENSION_P, PERSONAL_ALLOWANCE_P, BASIC_RATE, PENSION_GROWTH_RATE } = params;
  const TAX_FREE_PORTION = 0.25, TAXED_PORTION = 0.75;
  const NET_FACTOR = TAX_FREE_PORTION + TAXED_PORTION * (1 - BASIC_RATE); // 0.85

  const len = END_AGE - START_AGE + 1;
  const timeline = new Array(len);

  let age = START_AGE;
  for (let idx = 0; idx < len; idx++, age++) {
    const pensionStart = Math.round(pensionP);
    const savingsStart = Math.round(savingsP);
    let taxPaid = 0;

    const statePensionIncome = age >= 67 ? STATE_PENSION_P : 0;
    const extra = adhoc[age] || 0;

    let need = requiredNetP + extra - statePensionIncome;
    if (need < 0) need = 0;

    // Use savings first
    const fromSavings = Math.min(need, savingsP);
    savingsP -= fromSavings;
    need -= fromSavings;

    if (need > 0 && pensionP > 0) {
      let allowanceLeft = PERSONAL_ALLOWANCE_P - statePensionIncome;
      if (allowanceLeft < 0) allowanceLeft = 0;

      const thresholdGrossWithinAllowance = Math.round(allowanceLeft / TAXED_PORTION);
      let grossRequired;
      if (need <= thresholdGrossWithinAllowance) {
        grossRequired = need;
      } else {
        const adjustedNeed = need - Math.round(allowanceLeft * BASIC_RATE);
        grossRequired = Math.round(adjustedNeed / NET_FACTOR);
      }
      const grossWithdraw = Math.min(grossRequired, pensionP);

      const taxablePortion = Math.round(grossWithdraw * TAXED_PORTION);
      const zeroTaxOnTaxable = Math.min(taxablePortion, allowanceLeft);
      let taxedAboveAllowance = taxablePortion - zeroTaxOnTaxable;
      if (taxedAboveAllowance < 0) taxedAboveAllowance = 0;

      const taxForThisWithdrawal = Math.round(taxedAboveAllowance * BASIC_RATE);
      taxPaid += taxForThisWithdrawal;

      const netFromPension = grossWithdraw - taxForThisWithdrawal;

      need -= netFromPension;
      if (need < 0) need = 0;

      pensionP -= grossWithdraw;
    }

    pensionP = Math.round(pensionP * addRate(PENSION_GROWTH_RATE));

    const pensionEnd = Math.round(pensionP);
    const savingsEnd = Math.round(savingsP);
    timeline[idx] = wealth(age, pensionStart, pensionEnd, savingsStart, savingsEnd, Math.round(taxPaid), Math.round(extra));
  }
  return timeline;
}

function strategy3(savingsP, pensionP, requiredNetP, adhoc, params) {
  const { START_AGE, END_AGE, STATE_PENSION_P, PERSONAL_ALLOWANCE_P, BASIC_RATE, PENSION_GROWTH_RATE } = params;
  const TAX_FREE_PORTION = 0.25, TAXED_PORTION = 0.75;
  const NET_FACTOR = TAX_FREE_PORTION + TAXED_PORTION * (1 - BASIC_RATE); // 0.85

  const len = END_AGE - START_AGE + 1;
  const timeline = new Array(len);

  let age = START_AGE;
  for (let idx = 0; idx < len; idx++, age++) {
    const pensionStart = Math.round(pensionP);
    const savingsStart = Math.round(savingsP);
    let taxPaid = 0;

    const statePensionIncome = age >= 67 ? STATE_PENSION_P : 0;
    const extra = adhoc[age] || 0;

    let need = requiredNetP + extra - statePensionIncome;
    if (need < 0) need = 0;

    if (need > 0 && pensionP > 0) {
      let allowanceLeft = PERSONAL_ALLOWANCE_P - statePensionIncome;
      if (allowanceLeft < 0) allowanceLeft = 0;

      const grossCapWithinAllowance = Math.round(allowanceLeft / TAXED_PORTION);
      const grossZeroCandidate = Math.min(need, grossCapWithinAllowance, pensionP);
      const grossZero = Math.round(grossZeroCandidate);

      if (grossZero > 0) {
        const taxableZero = Math.round(grossZero * TAXED_PORTION);
        const netZero = grossZero; // no tax
        pensionP -= grossZero;
        need -= netZero;
        if (need < 0) need = 0;
        const allowanceConsumed = Math.min(taxableZero, allowanceLeft);
        allowanceLeft -= allowanceConsumed;
        if (allowanceLeft < 0) allowanceLeft = 0;
      }

      if (need > 0 && savingsP > 0) {
        const fromSavings = Math.min(need, savingsP);
        savingsP -= fromSavings; need -= fromSavings; if (need < 0) need = 0;
      }

      if (need > 0 && pensionP > 0) {
        let adjustedNeed = need - Math.round(allowanceLeft * BASIC_RATE);
        if (adjustedNeed < 0) adjustedNeed = 0;
        const grossRequired = Math.round(adjustedNeed / NET_FACTOR);
        const grossWithdraw = Math.min(grossRequired, pensionP);

        const taxablePortion = Math.round(grossWithdraw * TAXED_PORTION);
        const zeroTaxOnTaxable = Math.min(taxablePortion, allowanceLeft);
        let taxedAboveAllowance = taxablePortion - zeroTaxOnTaxable;
        if (taxedAboveAllowance < 0) taxedAboveAllowance = 0;
        const taxForThisWithdrawal = Math.round(taxedAboveAllowance * BASIC_RATE);
        taxPaid += taxForThisWithdrawal;

        const netFromPension = grossWithdraw - taxForThisWithdrawal;

        pensionP -= grossWithdraw;
        need -= netFromPension; if (need < 0) need = 0;

        allowanceLeft -= zeroTaxOnTaxable; if (allowanceLeft < 0) allowanceLeft = 0;
      }
    }

    if (need > 0 && savingsP > 0) {
      const fromSavings = Math.min(need, savingsP);
      savingsP -= fromSavings; need -= fromSavings; if (need < 0) need = 0;
    }

    pensionP = Math.round(pensionP * addRate(PENSION_GROWTH_RATE));

    timeline[idx] = wealth(age, Math.round(pensionStart), Math.round(pensionP), Math.round(savingsStart), Math.round(savingsP), Math.round(taxPaid), Math.round(extra));
  }
  return timeline;
}

function strategy3A(savingsP, pensionP, requiredNetP, adhoc, params) {
  const { START_AGE, END_AGE, STATE_PENSION_P, PERSONAL_ALLOWANCE_P, BASIC_RATE, PENSION_GROWTH_RATE, NO_INCOME_CONTRIBUTION_LIMIT_P } = params;
  const TAX_FREE_PORTION = 0.25, TAXED_PORTION = 0.75;
  const NET_FACTOR = TAX_FREE_PORTION + TAXED_PORTION * (1 - BASIC_RATE); // 0.85
  const len = END_AGE - START_AGE + 1;
  const timeline = new Array(len);

  let age = START_AGE;
  for (let idx = 0; idx < len; idx++, age++) {
    const pensionStart = Math.round(pensionP);
    const savingsStart = Math.round(savingsP);
    let taxPaid = 0;

    const statePensionIncome = age >= 67 ? STATE_PENSION_P : 0;
    const extra = adhoc[age] || 0;

    let need = requiredNetP + extra - statePensionIncome;
    if (need < 0) need = 0;

    // Pay up to £3,600 gross (net £2,880) from savings into pension if age <= 75
    if (params.useStrategy3AContribution && age <= 75 && savingsP > 0) {
      const netCap = Math.round(NO_INCOME_CONTRIBUTION_LIMIT_P * (1 - BASIC_RATE)); // 2880
      const netFromSavings = Math.min(savingsP, netCap);
      if (netFromSavings > 0) {
        const gross = Math.round(netFromSavings / (1 - BASIC_RATE));
        savingsP -= netFromSavings;
        pensionP += gross;
      }
    }

    if (need > 0 && pensionP > 0) {
      let allowanceLeft = PERSONAL_ALLOWANCE_P - statePensionIncome;
      if (allowanceLeft < 0) allowanceLeft = 0;

      const grossCapWithinAllowance = Math.round(allowanceLeft / TAXED_PORTION);
      const grossZeroCandidate = Math.min(need, grossCapWithinAllowance, pensionP);
      const grossZero = Math.round(grossZeroCandidate);
      if (grossZero > 0) {
        const taxableZero = Math.round(grossZero * TAXED_PORTION);
        const netZero = grossZero;
        pensionP -= grossZero;
        need -= netZero; if (need < 0) need = 0;
        const consumed = Math.min(taxableZero, allowanceLeft);
        allowanceLeft -= consumed; if (allowanceLeft < 0) allowanceLeft = 0;
      }

      if (need > 0 && savingsP > 0) {
        const fromSavings = Math.min(need, savingsP);
        savingsP -= fromSavings; need -= fromSavings; if (need < 0) need = 0;
      }

      if (need > 0 && pensionP > 0) {
        let adjustedNeed = need - Math.round(allowanceLeft * BASIC_RATE);
        if (adjustedNeed < 0) adjustedNeed = 0;
        const grossRequired = Math.round(adjustedNeed / NET_FACTOR);
        const grossWithdraw = Math.min(grossRequired, pensionP);

        const taxablePortion = Math.round(grossWithdraw * TAXED_PORTION);
        const zeroTaxOnTaxable = Math.min(taxablePortion, allowanceLeft);
        let taxedAboveAllowance = taxablePortion - zeroTaxOnTaxable;
        if (taxedAboveAllowance < 0) taxedAboveAllowance = 0;
        const taxForThisWithdrawal = Math.round(taxedAboveAllowance * BASIC_RATE);
        taxPaid += taxForThisWithdrawal;

        const netFromPension = grossWithdraw - taxForThisWithdrawal;

        pensionP -= grossWithdraw;
        need -= netFromPension; if (need < 0) need = 0;

        allowanceLeft -= zeroTaxOnTaxable; if (allowanceLeft < 0) allowanceLeft = 0;
      }
    }

    if (need > 0 && savingsP > 0) {
      const fromSavings = Math.min(need, savingsP);
      savingsP -= fromSavings; need -= fromSavings; if (need < 0) need = 0;
    }

    pensionP = Math.round(pensionP * addRate(PENSION_GROWTH_RATE));

    timeline[idx] = wealth(age, Math.round(pensionStart), Math.round(pensionP), Math.round(savingsStart), Math.round(savingsP), Math.round(taxPaid), Math.round(extra));
  }

  return timeline;
}

function strategy4(savingsP, pensionP, requiredNetP, adhoc, params) {
  const { START_AGE, END_AGE, STATE_PENSION_P, PERSONAL_ALLOWANCE_P, BASIC_RATE, BASIC_RATE_BAND_P, PENSION_GROWTH_RATE } = params;
  const TAX_FREE_PORTION = 0.25, TAXED_PORTION = 0.75;

  const len = END_AGE - START_AGE + 1;
  const timeline = new Array(len);

  let age = START_AGE;
  for (let idx = 0; idx < len; idx++, age++) {
    const pensionStart = Math.round(pensionP);
    const savingsStart = Math.round(savingsP);
    let taxPaid = 0;

    const statePensionIncome = age >= 67 ? STATE_PENSION_P : 0;
    const extra = adhoc[age] || 0;

    let need = requiredNetP + extra - statePensionIncome;
    if (need < 0) need = 0;

    let allowanceLeft = PERSONAL_ALLOWANCE_P - statePensionIncome;
    if (allowanceLeft < 0) allowanceLeft = 0;

    // Step A: zero-tax UFPLS within allowance
    if (pensionP > 0 && allowanceLeft > 0) {
      const grossCapWithinAllowance = Math.round(allowanceLeft / TAXED_PORTION);
      const grossZero = Math.min(grossCapWithinAllowance, pensionP);
      if (grossZero > 0) {
        const taxableZero = Math.round(grossZero * TAXED_PORTION);
        const netZero = grossZero;
        pensionP -= grossZero;
        const consumed = Math.min(taxableZero, allowanceLeft);
        allowanceLeft -= consumed; if (allowanceLeft < 0) allowanceLeft = 0;
        // We'll apply net to needs later as a pool
        var netFromPensionTotal = netZero;
      } else {
        var netFromPensionTotal = 0;
      }
    } else {
      var netFromPensionTotal = 0;
    }

    // Step B: fill basic-rate band
    if (pensionP > 0) {
      let taxableFromStatePension = statePensionIncome - PERSONAL_ALLOWANCE_P;
      if (taxableFromStatePension < 0) taxableFromStatePension = 0;
      let remainingBasicBand = BASIC_RATE_BAND_P - taxableFromStatePension;
      if (remainingBasicBand < 0) remainingBasicBand = 0;

      if (remainingBasicBand > 0) {
        const grossFillTarget = Math.round((remainingBasicBand + allowanceLeft) / TAXED_PORTION);
        const grossFill = Math.min(grossFillTarget, pensionP);
        if (grossFill > 0) {
          const taxablePortion = Math.round(grossFill * TAXED_PORTION);
          const zeroTaxOnTaxable = Math.min(taxablePortion, allowanceLeft);
          let taxedAboveAllowance = taxablePortion - zeroTaxOnTaxable;
          if (taxedAboveAllowance < 0) taxedAboveAllowance = 0;

          const tax = Math.round(taxedAboveAllowance * BASIC_RATE);
          taxPaid += tax;
          const netFill = grossFill - tax;

          pensionP -= grossFill;
          allowanceLeft -= zeroTaxOnTaxable; if (allowanceLeft < 0) allowanceLeft = 0;

          netFromPensionTotal += netFill;
        }
      }
    }

    // Apply net pension to spending; surplus to savings
    if (netFromPensionTotal > 0) {
      const spendFromPension = Math.min(netFromPensionTotal, need);
      need -= spendFromPension;
      const surplus = netFromPensionTotal - spendFromPension;
      if (surplus > 0) savingsP += surplus;
    }

    // If still needed, top up from savings
    if (need > 0 && savingsP > 0) {
      const fromSavings = Math.min(need, savingsP);
      savingsP -= fromSavings; need -= fromSavings; if (need < 0) need = 0;
    }

    pensionP = Math.round(pensionP * addRate(PENSION_GROWTH_RATE));

    timeline[idx] = wealth(age, Math.round(pensionStart), Math.round(pensionP), Math.round(savingsStart), Math.round(savingsP), Math.round(taxPaid), Math.round(extra));
  }
  return timeline;
}

function strategy5(savingsP, pensionP, requiredNetP, adhoc, params) {
  const { START_AGE, END_AGE, STATE_PENSION_P, PERSONAL_ALLOWANCE_P, BASIC_RATE, PENSION_GROWTH_RATE } = params;
  const TAX_FREE_PORTION = 0.25, TAXED_PORTION = 0.75;
  const NET_FACTOR = TAX_FREE_PORTION + TAXED_PORTION * (1 - BASIC_RATE); // 0.85

  const len = END_AGE - START_AGE + 1;
  const timeline = new Array(len);

  let age = START_AGE;
  for (let idx = 0; idx < len; idx++, age++) {
    const pensionStart = Math.round(pensionP);
    const savingsStart = Math.round(savingsP);
    let taxPaid = 0;

    const statePensionIncome = age >= 67 ? STATE_PENSION_P : 0;
    const extra = adhoc[age] || 0;

    let need = requiredNetP + extra - statePensionIncome;
    if (need < 0) need = 0;

    if (need > 0 && pensionP > 0) {
      let allowanceLeft = PERSONAL_ALLOWANCE_P - statePensionIncome;
      if (allowanceLeft < 0) allowanceLeft = 0;

      const thresholdGrossWithinAllowance = Math.round(allowanceLeft / TAXED_PORTION);
      let grossRequired;
      if (need <= thresholdGrossWithinAllowance) {
        grossRequired = need;
      } else {
        const adjustedNeed = need - Math.round(allowanceLeft * BASIC_RATE);
        grossRequired = Math.round(adjustedNeed / NET_FACTOR);
      }
      const grossWithdraw = Math.min(grossRequired, pensionP);

      const taxablePortion = Math.round(grossWithdraw * TAXED_PORTION);
      const zeroTaxOnTaxable = Math.min(taxablePortion, allowanceLeft);
      let taxedAboveAllowance = taxablePortion - zeroTaxOnTaxable;
      if (taxedAboveAllowance < 0) taxedAboveAllowance = 0;

      const tax = Math.round(taxedAboveAllowance * BASIC_RATE);
      taxPaid += tax;

      const netFromPension = grossWithdraw - tax;
      pensionP -= grossWithdraw;

      if (netFromPension >= need) {
        const surplus = netFromPension - need;
        need = 0;
        if (surplus > 0) savingsP += surplus;
      } else {
        need -= netFromPension;
      }
    }

    if (need > 0 && savingsP > 0) {
      const fromSavings = Math.min(need, savingsP);
      savingsP -= fromSavings; need -= fromSavings; if (need < 0) need = 0;
    }

    pensionP = Math.round(pensionP * addRate(PENSION_GROWTH_RATE));

    timeline[idx] = wealth(age, Math.round(pensionStart), Math.round(pensionP), Math.round(savingsStart), Math.round(savingsP), Math.round(taxPaid), Math.round(extra));
  }
  return timeline;
}

// Report builder
function generateComparisonReport(savings, pension, requiredAmounts, targetAges, adhoc, params) {
  const len = params.END_AGE - params.START_AGE + 1;

  const timelinesByAmt = requiredAmounts.map(req => {
    return {
      s1: strategy1(savings, pension, req, adhoc, params),
      s2: strategy2(savings, pension, req, adhoc, params),
      s3: strategy3(savings, pension, req, adhoc, params),
      s3a: strategy3A(savings, pension, req, adhoc, params),
      s4: strategy4(savings, pension, req, adhoc, params),
      s5: strategy5(savings, pension, req, adhoc, params),
    };
  });

  const agesToUse = (targetAges && targetAges.length > 0) ? targetAges : [params.END_AGE];

  const strategyRowTitles = ["Strategy1", "Strategy2", "Strategy3", "Strategy3A", "Strategy4", "Strategy5"];
  const strategyClasses = ["strategy-1","strategy-2","strategy-3","strategy-3a","strategy-4","strategy-5"];

  const growthRatePercent = (params.PENSION_GROWTH_RATE * 100).toFixed(2);

  let html = '';
  html += `<div class="summary-block">
    <h3>Initial Parameters</h3>
    <p><strong>Initial Savings:</strong> ${formatGBP(savings)}</p>
    <p><strong>Initial Pension:</strong> ${formatGBP(pension)}</p>
    <p><strong>Example Annual Spending Amounts:</strong> ${requiredAmounts.map(formatGBP).join(', ')}</p>
    <p><strong>Target Ages:</strong> ${agesToUse.join(', ')}</p>
    <p><strong>Ad hoc withdrawals:</strong> ${
      (Object.keys(adhoc).length === 0)
        ? 'None'
        : Object.keys(adhoc).sort((a,b)=>a-b).map(a => `Age ${a}: ${formatGBP(adhoc[a])}`).join('; ')
    }</p>
  </div>`;

  for (const age of agesToUse) {
    const idx = Math.max(0, Math.min(len - 1, age - params.START_AGE));

    // compute max per column
    const maxForCol = requiredAmounts.map((_, j) => {
      const tb = timelinesByAmt[j];
      let m = tb.s1[idx].totalEnd();
      m = Math.max(m, tb.s2[idx].totalEnd());
      m = Math.max(m, tb.s3[idx].totalEnd());
      m = Math.max(m, tb.s3a[idx].totalEnd());
      m = Math.max(m, tb.s4[idx].totalEnd());
      m = Math.max(m, tb.s5[idx].totalEnd());
      return m;
    });

    html += `<h2 class="age-title">Results at Age ${age}</h2>
      <table>
        <thead>
          <tr>
            <th class="strategy-column">Strategy</th>
            ${requiredAmounts.map(a=>`<th>${formatGBP(a)}</th>`).join('')}
          </tr>
        </thead>
        <tbody>
    `;

    for (let s = 0; s < strategyRowTitles.length; s++) {
      html += `<tr class="${strategyClasses[s]}"><td class="strategy-column">${strategyRowTitles[s]}</td>`;
      for (let j = 0; j < requiredAmounts.length; j++) {
        const tb = timelinesByAmt[j];
        const cell =
          s === 0 ? tb.s1[idx].totalEnd() :
          s === 1 ? tb.s2[idx].totalEnd() :
          s === 2 ? tb.s3[idx].totalEnd() :
          s === 3 ? tb.s3a[idx].totalEnd() :
          s === 4 ? tb.s4[idx].totalEnd() :
          tb.s5[idx].totalEnd();
        const isBest = cell === maxForCol[j];
        const extraClass = isBest ? ' best' : '';
        html += `<td class="currency${extraClass}">${formatGBP(cell)}</td>`;
      }
      html += `</tr>`;
    }

    html += `</tbody></table>`;
  }

  html += `<div class="footer-block">
    <h3>Strategy Descriptions:</h3>
    <ul>
      <li><strong>Strategy 1:</strong> Use savings first. Then one-off 25% tax-free lump sum into savings. Then drawdown remaining pension (tax rules apply).</li>
      <li><strong>Strategy 2:</strong> Use savings first, then UFPLS (25% tax-free / 75% taxable).</li>
      <li><strong>Strategy 3:</strong> UFPLS within allowance first, use savings to avoid tax, then UFPLS taxed at basic-rate as needed.</li>
      <li><strong>Strategy 3A:</strong> Same as Strategy 3, plus annual £3,600 gross contribution (net £2,880 with 20% relief) at ages ≤ 75.</li>
      <li><strong>Strategy 4:</strong> Fill personal allowance (0%) then fill basic-rate band (20%)—surplus to savings.</li>
      <li><strong>Strategy 5:</strong> Draw primarily from pension using UFPLS; only use savings for shortfall.</li>
    </ul>
    <h3>Assumptions</h3>
    <ul>
      <li><strong>Pension growth rate above inflation:</strong> ${growthRatePercent}%</li>
      <li><strong>Personal allowance:</strong> ${formatGBP(params.PERSONAL_ALLOWANCE_P)}</li>
      <li><strong>State pension (annual):</strong> ${formatGBP(params.STATE_PENSION_P)}</li>
      <li><strong>Basic-rate band width:</strong> ${formatGBP(params.BASIC_RATE_BAND_P)}</li>
      <li><strong>Savings interest:</strong> None (and no inflation either)</li>
    </ul>
    <p><em>Generated on: ${new Date().toISOString().replace('T',' ').slice(0,19)}</em></p>
  </div>`;

  return html;
}

// UI logic
const el = (id) => document.getElementById(id);
const getParams = () => {
  const START_AGE = Number(el('startAge').value || 61);
  const END_AGE = Number(el('endAge').value || 99);
  const PENSION_GROWTH_RATE = Number(String(el('pensionGrowthRate').value || '0.04'));
  const PERSONAL_ALLOWANCE_P = toPence(el('personalAllowance').value || '12570.00');
  const STATE_PENSION_P = toPence(el('statePension').value || '11973.00');
  const BASIC_RATE = Number(String(el('basicRate').value || '0.20'));
  const BASIC_RATE_BAND_P = toPence(el('basicRateBand').value || '37700.00');
  const NO_INCOME_CONTRIBUTION_LIMIT_P = toPence(el('noIncomeContributionLimit').value || '3600.00');
  const useStrategy3AContribution = el('useStrategy3AContribution').checked;

  return {
    START_AGE, END_AGE,
    PENSION_GROWTH_RATE,
    PERSONAL_ALLOWANCE_P, STATE_PENSION_P,
    BASIC_RATE, BASIC_RATE_BAND_P, NO_INCOME_CONTRIBUTION_LIMIT_P,
    useStrategy3AContribution
  };
};

const handleGenerate = () => {
  try {
    const savings = toPence(el('initialSavings').value || '0');
    const pension = toPence(el('initialPension').value || '0');

    const spendingStr = el('spendingAmounts').value || '';
    const requiredAmounts = (spendingStr.split(',').map(s => s.trim()).filter(Boolean).map(toPence));
    if (requiredAmounts.length === 0) {
      alert('Please provide at least one annual spending amount.');
      return;
    }

    const agesStr = el('targetAges').value || '';
    const targetAges = agesStr
      ? agesStr.split(',').map(s => Number(s.trim())).filter(n => !Number.isNaN(n))
      : [];

    const adhoc = parseAdhoc(el('adhocWithdrawals').value || '');

    const params = getParams();
    if (params.END_AGE < params.START_AGE) {
      alert('End age must be greater than or equal to start age.');
      return;
    }

    // Basic validation of target ages
    for (const a of targetAges) {
      if (a < params.START_AGE || a > params.END_AGE) {
        alert(`Target age ${a} must be between ${params.START_AGE} and ${params.END_AGE}.`);
        return;
      }
    }

    const html = generateComparisonReport(savings, pension, requiredAmounts, targetAges, adhoc, params);
    el('report').innerHTML = html;
  } catch (e) {
    console.error(e);
    alert('Failed to generate report: ' + (e?.message || e));
  }
};

const handleDownload = () => {
  const reportDiv = el('report');
  if (!reportDiv.innerHTML.trim()) {
    alert('Please generate a report first.');
    return;
  }

  const params = getParams();
  const growthRatePercent = (params.PENSION_GROWTH_RATE * 100).toFixed(2);

  // Compose a standalone HTML file with inline styles (reuse styles.css link)
  const fullHtml = `
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Pension Strategy Comparison Report</title>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  ${document.querySelector('link[href="styles.css"]') ? '' : ''}
</style>
<link rel="stylesheet" href="styles.css">
</head>
<body>
<div class="app-container">
  <div class="card">
    <h1>Pension Strategy Comparison Report</h1>
    ${reportDiv.innerHTML}
  </div>
</div>
</body>
</html>
  `.trim();

  const blob = new Blob([fullHtml], { type: 'text/html;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  const ts = new Date().toISOString().replace(/[:.]/g,'-').slice(0,19);
  a.href = url;
  a.download = `pension-strategy-comparison-${ts}.html`;
  document.body.appendChild(a);
  a.click();
  setTimeout(() => {
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, 0);
};

window.addEventListener('DOMContentLoaded', () => {
  el('generateBtn').addEventListener('click', handleGenerate);
  el('downloadBtn').addEventListener('click', handleDownload);
});
