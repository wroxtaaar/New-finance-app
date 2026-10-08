# New Finance App — Transaction Detection Core

The first milestone is **transaction detection only**. No budgets, categories, cloud sync, OCR, email or Account Aggregator yet.

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

## Detection pipeline

SMS / payment notification -> allowlist -> raw event -> deterministic parser -> stable identity -> cross-source match -> one normalized transaction

## Definition of "perfect"

We should not call the detector perfect until it passes a real-device corpus containing every bank/card/UPI SMS format you receive, notification variants, multipart SMS, delayed notifications, refunds, reversals, failed transactions, balance-only messages, OTPs, promotions and repeated alerts.

The next engineering step is therefore to build that anonymized test corpus and add bank-specific parser rules from real samples. That is more valuable at this stage than adding AI.

## Google Play

SMS permissions are sensitive/restricted. Current Google Play policy has specific requirements and exceptions for SMS-based money management. Initial development can be tested on a personal device, but Play distribution needs a separate policy review before release.
