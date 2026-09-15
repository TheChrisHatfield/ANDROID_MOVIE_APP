<!-- BOOTSTRAP: hive_planner init -->

# Change Impact Policy

**Hook:** `change.impact`, `change.validation`

Generate an impact packet before non-trivial edits:

```powershell
py -3.11 -m hive_planner impact --feature <feature> --files <path>
py -3.11 -m hive_planner impact --feature <feature> --mode bugfix --files <path>
```
