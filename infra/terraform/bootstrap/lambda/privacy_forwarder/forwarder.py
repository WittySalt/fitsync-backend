"""Forward mail received at privacy@<domain> to the owner's inbox.

SES stores the raw message in S3 and then invokes this function with the receipt
notification. The function reads the stored copy, rewrites the headers so that SES
may send it from our own verified domain, and sends it on with SendRawEmail.

Standard library and boto3 only (boto3 ships with the Lambda Python runtime).
"""

import logging
import os
from email import policy
from email.parser import BytesParser
from email.utils import formataddr, getaddresses

LOG = logging.getLogger()
LOG.setLevel(logging.INFO)

SUBJECT_PREFIX = "[privacy@] "

# Headers that would break or misrepresent the forwarded copy: the original DKIM
# signature no longer matches once the headers change, and the bounce address and
# Sender belong to the original sending system.
STRIPPED_HEADERS = ("DKIM-Signature", "Return-Path", "Sender")

_ses = None
_s3 = None


def _clients():
    global _ses, _s3
    if _ses is None:
        import boto3  # imported lazily so the rewrite logic can be tested without boto3

        _ses = boto3.client("ses")
        _s3 = boto3.client("s3")
    return _ses, _s3


def rewrite(raw: bytes, sender: str, sender_name: str) -> bytes:
    """Return the message with From, Reply-To and Subject rewritten for forwarding."""
    msg = BytesParser(policy=policy.default).parsebytes(raw)

    original_from = msg.get("From", "")
    # Replies go back to whoever wrote the mail, not to the no-reply sender.
    reply_to = msg.get("Reply-To") or original_from

    for name in STRIPPED_HEADERS + ("From", "Reply-To"):
        del msg[name]

    msg["From"] = formataddr((sender_name, sender))
    addresses = [addr for addr in getaddresses([str(reply_to)]) if addr[1]]
    if addresses:
        msg["Reply-To"] = ", ".join(formataddr(addr) for addr in addresses)

    subject = str(msg.get("Subject", ""))
    del msg["Subject"]
    msg["Subject"] = subject if subject.startswith(SUBJECT_PREFIX) else SUBJECT_PREFIX + subject

    return msg.as_bytes(policy=policy.SMTP)


def handler(event, _context):
    bucket = os.environ["MAIL_BUCKET"]
    prefix = os.environ["MAIL_PREFIX"]
    forward_to = os.environ["FORWARD_TO"]
    sender = os.environ["SENDER_ADDRESS"]
    sender_name = os.environ["SENDER_NAME"]

    ses, s3 = _clients()
    for record in event.get("Records", []):
        receipt = record["ses"]["receipt"]
        message_id = record["ses"]["mail"]["messageId"]

        # The raw copy stays in S3 for the lifecycle period either way, so nothing is lost.
        if receipt.get("virusVerdict", {}).get("status") == "FAIL":
            LOG.warning("message %s failed the virus scan; not forwarded", message_id)
            continue

        obj = s3.get_object(Bucket=bucket, Key=prefix + message_id)
        forwarded = rewrite(obj["Body"].read(), sender, sender_name)

        result = ses.send_raw_email(
            Source=sender,
            Destinations=[forward_to],
            RawMessage={"Data": forwarded},
        )
        # Message ids only: the log never carries addresses or content.
        LOG.info("forwarded %s as %s", message_id, result["MessageId"])

    return {"status": "ok"}
