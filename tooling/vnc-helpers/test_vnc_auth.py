import socket
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend

def reverse_bits(b):
    return int('{:08b}'.format(b)[::-1], 2)

def make_key(password):
    # VNC reverses the bits of each byte of the password
    pwd = password.encode('utf-8')[:8].ljust(8, b'\x00')
    key = bytes(reverse_bits(b) for b in pwd)
    return key

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.connect(("127.0.0.1", 5900))
s.recv(12)
s.send(b"RFB 003.008\n")
num_auths = s.recv(1)[0]
auths = s.recv(num_auths)

# send AuthType 2
s.send(b"\x02")
challenge = s.recv(16)

# Encrypt challenge
key = make_key("vncpass")
cipher = Cipher(algorithms.TripleDES(key * 3), modes.ECB(), backend=default_backend())
encryptor = cipher.encryptor()
response = encryptor.update(challenge) + encryptor.finalize()

s.send(response)
result = s.recv(4)
print("Auth result:", result)
if result != b'\x00\x00\x00\x00':
    print("Failed reason:", s.recv(1024))
