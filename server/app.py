from flask import Flask
from flask.globals import request
from flask.json import jsonify

app = Flask(__name__)

TA1 = 0
TA3 = 0
TB1 = 0
TB3 = 0

has_A_send = False
has_B_send= False

C = 340

@app.route('/locate/:s',methods=['POST'])
def handle_locate(s):
    global has_A_send
    global has_B_send
    if s=='A':
        global TA1
        global TA3
        TA1 = request.json.TA1
        TA3 = request.json.TA3
        has_A_send=True
        print("Recv A ",TA1,TA3);
    if s=='B':
        global TB1
        global TB3
        TB1 = request.json.TB1
        TB3 = request.json.TB3
        has_B_send=True
        print("Recv B ",TB1,TB3);
    if has_A_send and has_B_send:
        dis = C/2*((TA3-TA1)-(TB3-TB1))
        print("Distance: ",dis)
    return jsonify(),200

if __name__ == "__main__":
    app.run()

