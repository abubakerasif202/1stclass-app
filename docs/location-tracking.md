# Active-shift location tracking

Location capture runs only for a signed-in driver whose persisted shift is `ON_DUTY` or
`ON_BREAK`, and only while coarse or precise location permission is available. The foreground
service requests balanced-power fixes every 20 seconds, permits 15-second moving updates, batches
for up to 30 seconds, and uses a 10 metre minimum displacement. This is frequent enough for
transport operations without continuously requesting maximum-rate GPS.

Every accepted fix is committed to Room together with a pending `LOCATION_POINT_CREATED` sync
operation. No point is marked synced until a future TMS integration receives a real acknowledgement.
Unsynced points are never pruned. After TMS integration, add an acknowledged-only retention policy
(for example, prune synced points older than the agreed operational/audit period); never delete
pending or failed points automatically.

## Google Maps key

The app builds without a Maps key. To render Google map tiles locally, add this untracked entry to
the repository-root `local.properties` file:

```properties
MAPS_API_KEY=your_restricted_android_maps_key
```

Restrict the key in Google Cloud to the Android application ID and signing-certificate SHA-1.
`local.properties` is ignored by Git. CI can alternatively provide a `MAPS_API_KEY` environment
variable.
