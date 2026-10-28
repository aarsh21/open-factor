#!/usr/bin/env python3
"""Create new private local credentials; output only their file path."""
from pathlib import Path
import os
import secrets

destination = Path(__file__).parent / '.env'
fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as stream:
    stream.write(f'RUSTFS_ACCESS_KEY=of-{secrets.token_hex(12)}\n')
    stream.write(f'RUSTFS_SECRET_KEY={secrets.token_urlsafe(48)}\n')
    stream.write(f'MEDIA_ADMIN_TOKEN={secrets.token_urlsafe(48)}\n')
    stream.write('S3_BUCKET=open-factor-videos\n')
print(destination.resolve())
