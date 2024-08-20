import os
import json
import http.client
import requests
from flask import Flask, request, render_template
from dotenv import load_dotenv

load_dotenv()

speech_invoke_url = 'https://clovaspeech-gw.ncloud.com/external/v1/8858/30a1feeb7de447a76c2dd8ad0b9c18666339a130d19ab570efd4f881894b32c4'
speech_secret = '7f66cefcbb5b442d842b2abe9bc67d9b'
clova_studio_host = 'clovastudio.apigw.ntruss.com'
clova_studio_api_key = 'NTA0MjU2MWZlZTcxNDJiYxteJcS/tQtG1yQbbXdxii3loCK33bmD1iAr3WX+ViJz'
clova_studio_api_key_primary_val = 'b6wvsrbh5Tz7FuRhCwHGuEDS46fPXrEp1KzWOgEh'
request_id = 'cb7e6561-3903-4d8a-b938-eb8da085be13'

app = Flask(__name__)
UPLOAD_FOLDER = './uploads'

if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

class ClovaSpeechClient:
    def req_upload(self, file, completion='sync'):
        request_body = {
            'language': 'ko-KR',
            'completion': completion
        }
        headers = {
            'Accept': 'application/json;UTF-8',
            'X-CLOVASPEECH-API-KEY': speech_secret
        }
        files = {
            'media': open(file, 'rb'),
            'params': (None, json.dumps(request_body, ensure_ascii=False).encode('UTF-8'), 'application/json')
        }
        response = requests.post(headers=headers, url=speech_invoke_url + '/recognizer/upload', files=files)
        return response

class CompletionExecutor:
    def __init__(self, host, api_key, api_key_primary_val, request_id):
        self._host = host
        self._api_key = api_key
        self._api_key_primary_val = api_key_primary_val
        self._request_id = request_id

    def _send_request(self, completion_request):
        headers = {
            'Content-Type': 'application/json; charset=utf-8',
            'X-NCP-CLOVASTUDIO-API-KEY': self._api_key,
            'X-NCP-APIGW-API-KEY': self._api_key_primary_val,
            'X-NCP-CLOVASTUDIO-REQUEST-ID': self._request_id
        }

        conn = http.client.HTTPSConnection(self._host)
        conn.request('POST', '/testapp/v1/api-tools/summarization/v2/c784ca5404e74ca598dc951b8c992029', json.dumps(completion_request), headers)
        response = conn.getresponse()
        result = json.loads(response.read().decode(encoding='utf-8'))
        conn.close()
        return result

    def execute(self, completion_request):
        res = self._send_request(completion_request)
        if res['status']['code'] == '20000':
            return res['result']['text']
        else:
            return 'Error'

@app.route('/', methods=['GET', 'POST'])
def index():
    transcript = None
    summary = None

    if request.method == 'POST':
        if 'file' not in request.files:
            return 'No file part'
        file = request.files['file']
        if file.filename == '':
            return 'No selected file'

        file_path = os.path.join(UPLOAD_FOLDER, file.filename)
        file.save(file_path)

        client = ClovaSpeechClient()
        res = client.req_upload(file=file_path, completion='sync')
        result = res.json()

        segments = result.get('segments', [])
        transcript = " ".join([segment['text'] for segment in segments])

        completion_executor = CompletionExecutor(
            host=clova_studio_host,
            api_key=clova_studio_api_key,
            api_key_primary_val=clova_studio_api_key_primary_val,
            request_id=request_id
        )
        request_data = {
            "texts": [transcript],
            "segMinSize": 300,
            "includeAiFilters": False,
            "autoSentenceSplitter": True,
            "segCount": -1,
            "segMaxSize": 1000
        }
        summary = completion_executor.execute(request_data)

    uploaded_files = os.listdir(UPLOAD_FOLDER)

    return render_template('index.html', transcript=transcript, summary=summary, files=uploaded_files)

if __name__ == '__main__':
    app.run(debug=True)
