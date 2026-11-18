import json

"""
Entry point for user modules.

run(code, ctx_json) executes the module source in a fresh namespace, then calls the function the
module defines — whatever its name is, because that name is the module's own:

    def deadlift_form(form):
        ...

The single argument is the value of the item the module was created with, converted to its declared
type (int, float, boolean, string). Images are never offered as module inputs.

The function returns the prescription for the sets it covers, in this order:

    return sets, reps, percent, time

e.g.  return -1, -1, -10.0, 60   → one set less, one rep less, 10% lighter, +60 s rest
      return 0, 0, 0.0, 0        → no change
      None                       → no change
"""


def _coerce(value, kind):
    """Turn the stored string into the item's declared type."""
    if value is None or value == "":
        return None
    kind = (kind or "string").lower()
    try:
        if kind == "int":
            return int(float(value))
        if kind == "float":
            return float(value)
        if kind == "boolean":
            return str(value).strip().lower() in ("1", "true", "yes", "y", "on")
    except (TypeError, ValueError):
        return value
    return value


def _entry_point(scope):
    """The first function the module defines; its name is the module's, so we never look it up."""
    for name, value in scope.items():
        if name.startswith("__") or name in ("json", "math"):
            continue
        if callable(value) and getattr(value, "__module__", None) == "user_module":
            return value
    return None


def run(code, ctx_json):
    context = json.loads(ctx_json) if ctx_json else {}
    scope = {"__name__": "user_module"}
    exec(compile(code, "<module>", "exec"), scope)

    fn = _entry_point(scope)
    if fn is None:
        raise ValueError("module defines no function")

    info = context.get("item") or {}
    item = _coerce(info.get("value"), info.get("type"))
    result = fn(item)

    if result is None:
        return "null"
    if isinstance(result, (list, tuple)):
        return json.dumps(list(result))
    raise TypeError("the function must return sets, reps, percent, time")
