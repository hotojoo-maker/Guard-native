from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

key = bytes(16)
pt = bytes(16)
cipher = Cipher(algorithms.AES(key), modes.ECB())
enc = cipher.encryptor()
ct = enc.update(pt) + enc.finalize()
print("AES-128(0,0) =", ct.hex())

# NIST GCM empty
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
tag = AESGCM(key).encrypt(bytes(12), b"", None)
print("GCM empty tag =", tag.hex())
