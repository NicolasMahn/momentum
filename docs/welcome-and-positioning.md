# Momentum welcome page and positioning

Direction agreed on 2026-10-06 for a future first page before setup. Momentum helps people record what matters to them, notice patterns, and follow their own goals. Mo offers encouragement; the app does not promise achievement or treat its score as a measure of the person.

## Welcome page

> You choose what matters to you. Mo helps you record your days, notice patterns, and see your progress.
>
> Your goals extend beyond the app—and a difficult day doesn’t erase the steps you’ve already taken.

Pair this with a happy Mo welcoming the person and wishing them well. Use the established [Mo design](mo.md), with a friendly smile and a small welcoming gesture. Keep the page simple: Mo, the welcome text, and one action leading to setup. It needs no extra heading or explanation of the reward mechanics.

A possible short greeting from Mo:

> Good luck. I’m rooting for you.

## Marketing promise

Lead with the same promise: **You choose the direction. Mo helps you keep track.**

Describe recording, patterns, and encouragement as the app’s contribution. People choose their goals, the features they use, and how much structure they want. Tracking can support change; entering a record is not proof that a wider goal has been achieved.

- Explain each view's goal where the person sets it up. Some goals ask for a measurement or reflection; others ask for recorded completion. The app's introduction should not describe all rewards as rewards for logging.
- Keep tracking optional where daily participation has not been chosen. Occasional use is a valid way to use Momentum.
- Preserve recorded history and longer-term progress when a consecutive streak resets.
- Use language that supports choice without suggesting that difficulty is entirely someone’s fault. Avoid “success is all up to you,” guaranteed transformation, and promises of recovery from substance dependence.
- Keep substance outcomes calm and factual. Recording use or confirming none provides information; rewards never judge amounts or the person.

Before shipping, review the guilt, anger, and loss described in the existing [Mo specification](mo.md) against this promise. The welcome text and the experience after a difficult week must agree. This direction does not change current behavior by itself.

## Goals for each view

Each view tracks progress toward a specific goal. Recording is how Momentum knows what happened; what satisfies the goal belongs to that view.

| Kind | What satisfies its configured goal |
| --- | --- |
| Measurement | Record a measurement, regardless of its value or direction. |
| Questions | Record the answers for the day being asked about, regardless of answer quality. |
| Time | Meet the weekly target with recorded minutes. A completion mark without minutes invents no duration. |
| Activity | Record activity on the configured number of days. |
| Checklist | Complete the due actions under its configured rules. Partial records remain facts. |
| Substances | Follow the chosen logging or staying-away goal. Amounts do not increase the reward. |

Goals, reward participation and Mo participation are explicit configuration, not rules inferred
from a tracker's name. Exception-only checklist or staying-away rules may assume a day is kept
for rewards when no exception was recorded. That assumption creates no factual record: an
unrecorded day does not become a tick, a confirmed none-day, a measurement, an answer or minutes,
and factual counts and rates stay unchanged. Under ordinary logging, missing records remain
unknown.

Keep measurement trends separate from streak rewards. The app can help someone notice a
pattern without treating a value moving in the preferred direction as success or failure.
This is a product choice; the research below does not establish the best reward rule for each kind.

## Research behind the direction

- [Ryan and Deci on self-determination](https://selfdeterminationtheory.org/SDT/documents/2000_RyanDeci_SDT.pdf): motivation benefits from support for autonomy, competence, and connection.
- [Harkin et al. on progress monitoring](https://pubmed.ncbi.nlm.nih.gov/26479070/): monitoring interventions improved goal attainment on average across 138 experiments; this is not evidence of Momentum's effectiveness.
- [Madigan et al. on self-weighing](https://link.springer.com/article/10.1186/s12966-015-0267-4): adding self-weighing and self-regulation to behavioral weight-management programs may help; evidence for weighing alone was insufficient. Monitoring is a possible support, not an assurance of a weight outcome.
- [Critical analysis of digital health](https://www.frontiersin.org/journals/communication/articles/10.3389/fcomm.2019.00052/full): framing health entirely as individual responsibility can overlook social circumstances and resources.
- [Alberts et al. on behavior-change technology](https://arxiv.org/abs/2402.00121): engagement with an app can become the design’s priority instead of lasting change outside it.
- [Silverman and Barasch on logged streaks](https://doi.org/10.1093/jcr/ucac029): displaying intact versus broken streaks affected subsequent participation, including when previous behavior was held constant. The studies did not test addiction recovery.
