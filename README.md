# New Finance App — Transaction Detection Core

The first milestone is **transaction detection only**. The current detection priority is:

1. **Actual bank emails — source of truth** (planned next).
2. SMS, payment-app notifications and email notifications — fallback/fast signals.
3. Historical SMS/email backfill — recovery/reconciliation.

## Implemented

- Real-time bank SMS capture with sender allowlisting.
- SMS inbox backfill/rescan with stable inbox message IDs.
- Payment-app notification capture using NotificationListenerService.
- Notification repost suppression.
- Local Room database for raw events and normalized transactions.
- Deterministic extraction of amount, debit/credit, account last four, bank, merchant and UTR/RRN/reference.
- Cross-source deduplication using reference + amount/direction first, then amount/direction/account/merchant/time-window matching.
- When SMS and notification describe the same transaction, their sources and raw event IDs are merged instead of creating a second transaction.
- Unknown/unparsed events are retained as parser candidates instead of silently discarded.
- Original event timestamps are preserved.
- WorkManager rescan after boot and manual rescan.
- **Debug SMS corpus export**: exports every SMS currently stored by Android to JSON, including non-financial messages.

## Exporting the SMS corpus

1. Install the debug build on the phone.
2. Open **Transaction Detector**.
3. Tap **Allow SMS** and grant READ_SMS.
4. Tap **Export all SMS**.
5. Choose a location such as Downloads/sms_corpus.json.
6. Upload that JSON file for parser analysis.

The exporter intentionally includes **all stored SMS**, not only messages that look financial. Non-financial messages are valuable negative examples for avoiding false positives.

The JSON contains:

- Android SMS _id
- thread ID
- sender/address
- timestamp in milliseconds
- message type
- read/seen state
- complete message body

The exporter streams records directly to the selected file rather than building the entire corpus in memory.

## Detection pipeline

SMS / payment notification -> allowlist -> raw event -> deterministic parser -> stable identity -> cross-source match -> one normalized transaction

The target architecture will additionally include:

Actual bank email -> email identity -> bank-specific email parser -> normalized transaction -> source of truth

Email notifications can provide a fast fallback, while the actual email remains the durable record.

## Definition of "perfect"

We should not call the detector perfect until it passes a real-device corpus containing every bank/card/UPI SMS format you receive, notification variants, multipart SMS, delayed notifications, refunds, reversals, failed transactions, balance-only messages, OTPs, promotions and repeated alerts.

The next engineering step is therefore to build that real test corpus and add bank-specific parser rules from real samples. That is more valuable at this stage than adding AI.

## Google Play

SMS permissions are sensitive/restricted. Current Google Play policy has specific requirements and exceptions for SMS-based money management. Initial development can be tested on a personal device, but Play distribution needs a separate policy review before release.
