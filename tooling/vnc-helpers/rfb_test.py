import socket

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.connect(("127.0.0.1", 5900))
print("Server version:", s.recv(12))
s.send(b"RFB 003.008\n")
num_auths = s.recv(1)[0]
if num_auths == 0:
    print("Connection failed:", s.recv(1024))
else:
    auths = s.recv(num_auths)
    print("Auth types offered:", list(auths))
