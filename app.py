import os
import json
import http.client
import requests
from flask import Flask, request, render_template
from pymongo import MongoClient
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

speech_invoke_url = 'https://clovaspeech-gw.ncloud.com/external/v1/8872/2b1a47e9d180934aa52d90f9747001aa9042f162473d37445cb105f1430d65be'
speech_secret = 'd71d8c703cd6430eb388bce807bbe12a'
clova_studio_host = 'clovastudio.apigw.ntruss.com'
clova_studio_api_key = 'NTA0MjU2MWZlZTcxNDJiY0cpBRl68rxRwwm98a19OHQhCTwcyjvbi0rHHH/Wv9nu'
clova_studio_api_key_primary_val = 'uEzhGyfgNc8ZQVEQASFNur74IYGBSWPEZzpcTE1S'
request_id = 'f08c7257-7594-428e-9888-829df9f50d54'

app = Flask(__name__)
UPLOAD_FOLDER = './uploads'

mongo_uri = "mongodb+srv://admin:adminPW@atlascluster.a68jlym.mongodb.net/?retryWrites=true&w=majority"      #수정필요!!
client = MongoClient(mongo_uri)

db = client.get_database('compareDB')
collection = db['profile']
correct_collection = db['compareDB']

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
        with open(file, 'rb') as f:
            files = {
                'media': f,
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
            return f"Error: {res['status']['message']}"

def calculate_tfidf_cosine_similarity(new_text, stored_texts):
    texts = stored_texts + [new_text]
    vectorizer = TfidfVectorizer().fit_transform(texts)
    vectors = vectorizer.toarray()
    cosine_similarities = cosine_similarity(vectors[-1:], vectors[:-1])
    return cosine_similarities[0]

@app.route('/', methods=['GET', 'POST'])
def index():
    transcript = None
    summary = None
    warning_message = ""

    if request.method == 'POST':
        if 'file' not in request.files or 'phone_number' not in request.form:
            return '파일이나 전화번호가 제공되지 않았습니다.'

        file = request.files['file']
        phone_number = request.form['phone_number']

        if file.filename == '':
            return '파일이 선택되지 않았습니다.'

        file_path = os.path.join(UPLOAD_FOLDER, file.filename)
        file.save(file_path)

        # 1. 음성 파일을 텍스트로 변환
        client = ClovaSpeechClient()
        res = client.req_upload(file=file_path, completion='sync')
        if res.status_code != 200:
            return f"Speech API Error: {res.status_code} - {res.text}"

        result = res.json()
        if 'segments' not in result:
            return "Error: No segments found in response."

        segments = result.get('segments', [])
        transcript = " ".join([segment['text'] for segment in segments])

        # 2. 요약 수행
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
        
        # 3. 유사도 판단
        stored_texts = [doc['speech'] for doc in collection.find({"$where": "this.speech.length <= 2000"})]
        long_summaries = [doc['summary'] for doc in collection.find({"$where": "this.speech.length > 2000"})]

        speech_similarity_scores = calculate_tfidf_cosine_similarity(transcript, stored_texts)
        summary_similarity_scores = calculate_tfidf_cosine_similarity(summary, long_summaries)

        if speech_similarity_scores.max() >= 0.8 or summary_similarity_scores.max() >= 0.8:
            matching_phone_number = False
            for correct_doc in correct_collection.find():
                keywords = correct_doc['keyword']  # keyword가 배열 형태라고 가정
                correct_phone_list = correct_doc['phoneNumber']
                for keyword in keywords:  # 배열 내의 각 키워드를 순차적으로 검사
                    if keyword in transcript or keyword in summary:
                        if phone_number in correct_phone_list:
                            matching_phone_number = True
                            warning_message = "보이스 피싱이 의심되는 전화입니다"
                            break
                        else:
                            warning_message = "보이스 피싱일 확률이 높습니다!"
                            break

            # 데이터 저장
            if transcript and summary:
                collection.insert_one({'speech': transcript, 'summary': summary, 'phonenumber': phone_number})


    uploaded_files = os.listdir(UPLOAD_FOLDER)

    return jsonify({
        "transcript": transcript,
        "summary": summary,
        "warning_message": warning_message
    })

if __name__ == '__main__':
    app.run(debug=True)
