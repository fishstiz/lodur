# Lodur

Logs the duration of resource pack reloading in Minecraft.

## Bulk Automated Testing

To test reloads in bulk, you can run the in-game command:

```
/lodur test run <count> [<minimize>]
```

| Argument | Mandatory | Description           |
|----------|-----------|-----------------------|
| count    | yes       | Number of reloads.    |
| minimize | no        | Minimizes the window. |

A report of the run statistics will be generated after the test.