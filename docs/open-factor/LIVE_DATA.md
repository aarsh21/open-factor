# Recorded training and muscle workload

Dashboard and Workout summarize recorded training instead of showing an empty Active Program comparison. Programs remain available from Workout. Weekly navigation shows Monday–Sunday bars, with actual values only through today. Sessions, working sets, volume and trained-muscle counts come from saved workouts and the current active session, deduplicated by workout ID and grouped by workout start date in the recorded time zone. Future workouts are excluded.

Only completed logical working sets contribute to workload. Warmups, incomplete sets, unilateral sides and Myo segments do not add extra working sets. Completing, unchecking, editing, finishing or discarding the active session recomputes the values from durable app state. A session counts once it contains a completed working set. Habits count saved workouts only. History/completion summaries can describe completed warmups as well, so their all-set count can differ from working-set analytics.

## Muscle accounting

The front/back anatomy map, workload table and exercise drilldowns use the catalog's primary and secondary muscle labels. Each completed logical set contributes once to each primary muscle and once to each distinct secondary muscle. A primary muscle cannot also receive a secondary contribution from the same set.

| Display | Calculation |
| --- | --- |
| Direct | Completed sets for which the muscle is primary |
| Indirect | Completed sets for which the muscle is secondary |
| Weighted total | Direct + Indirect × 0.5 |

For example, two bench-press working sets can produce Chest: 2 direct / 0 indirect / 2 total, Front Delts: 0 direct / 2 indirect / 1 total, and Triceps: 0 direct / 2 indirect / 1 total. The precise muscles depend on the exercise catalog entry. A workout's two sets remain two working sets even though they train several muscles.

Muscle workload offers Week (Monday through the selected date), 30 days and 90 days. It shows actual totals in the selected window, not normalized sets/week. Muscle selection opens its contributing exercises; an exercise drilldown exposes recorded sets and an Open exercise action. Bars compare the displayed muscle counts with the largest count in the view. The color legend communicates weighted-set bands. These are explicit accounting and display policies, not a physiological recovery estimate, strength score or prescribed training target.

Date-dependent summaries refresh on foreground resume and local-day changes. The [weight chart](BODY-CHARTS.md) uses daily body readings and a labeled seven-day mean. [Health Connect](HEALTH-CONNECT.md) supplies actual provider weight/body fat and daily step aggregates after consent; those imported values are separate from workout muscle accounting.

## Exercise and guidance coverage

Search covers all **1,345 catalog exercises**, with ranked exact names and aliases, abbreviations, reordered terms, combined name/equipment/muscle words and bounded one-edit typo matching. All equipment is the explicit default; muscle/equipment filters can narrow results and clearing them preserves the query. The full ranked list remains available rather than hiding matches inside collapsed groups. Favorites, custom exercises and gym profiles remain available.

Original video guidance covers **619 exercises** with **1,831 original video objects**; catalog coverage does not imply every exercise has a demonstration. Static/full builds package recovered photos, anatomy and instructions where available. Frameless video starts only after Play, cycles the available camera angles in one repeating playlist and uses the bounded cache. See [media packaging](MEDIA-PACKAGING.md) for inventory and missing-media states.

Earlier alpha source-only artifacts intentionally used fallback icons and limited licensed illustrations. Those historical packaging choices do not describe the current static-media app. The published [0.2.0](releases/v0.2.0.md) and [0.3.0](releases/v0.3.0.md) records remain specific to their artifacts; [0.4.0](releases/v0.4.0.md) records the current changes and verification scope.
