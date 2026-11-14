# Lodur

Lodur, or loading overlay duration, is a simple benchmarking tool for resource reloading.

## Duration Logging

The duration of resource reloads will be logged. This does not include the initial resource load on launch.

Look out for log entries that start with `[lodur]`

## Bulk Automated Testing

Resource reloading can be automated in bulk with the following command:

```
/lodur test run <count> [<minimize>] [<discovery>]
```

| Argument  | Mandatory | Description                                                 |
|-----------|-----------|-------------------------------------------------------------|
| count     | yes       | Number of reloads. Minimum of `1`.                          |
| minimize  | no        | Minimizes the window when starting. Defaults to `false`.    |
| discovery | no        | Includes pack discovery on each reload. Defaults to `true`. |

The run statistics will be logged to chat and saved to a CSV at `config/lodur/reports`.

## Reload without Discovery

Reload resource packs without initiating pack discovery with the following command:

```
/lodur reload false
```

Be warned that this may cause issues if the game attempts to read a resource that may no longer exist.