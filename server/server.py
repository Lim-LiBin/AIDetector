from flask import Flask, request, jsonify
from model_inference import ModelInference
from video_processor import VideoProcessor
from datetime import datetime, timezone
from urllib.parse import urlparse
import whois

app = Flask(__name__)

print("[서버 초기화] 모델 로딩 중...")
model = ModelInference('model.tflite')
video_processor = VideoProcessor(model)
print("[서버 초기화 완료]")


@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({'status': 'ok'}), 200


@app.route('/check_url', methods=['POST'])
def check_url():
    """
    URL 도메인 검사 API

    Request:  {"url": "https://example.com/..."}
    Response: {
        "domain": "example.com",
        "creation_date": "2024-01-15",
        "domain_age_days": 480,
        "is_suspicious": false,
        "message": "안전한 도메인입니다."
    }
    """
    try:
        data = request.get_json()
        if not data or 'url' not in data:
            return jsonify({'error': 'URL이 필요합니다'}), 400

        url = data['url']
        parsed = urlparse(url)
        domain = parsed.netloc or parsed.path

        # www. 제거
        if domain.startswith('www.'):
            domain = domain[4:]

        # 포트 번호 제거
        if ':' in domain:
            domain = domain.split(':')[0]

        print(f"\n[도메인 검사] {domain}")

        # 주요 플랫폼은 검사 스킵 (안전)
        safe_domains = [
            'youtube.com', 'youtu.be',
            'instagram.com',
            'facebook.com', 'fb.com',
            'tiktok.com',
            'twitter.com', 'x.com',
            'naver.com', 'blog.naver.com',
            'daum.net',
        ]

        for safe in safe_domains:
            if domain == safe or domain.endswith('.' + safe):
                return jsonify({
                    'domain': domain,
                    'creation_date': None,
                    'domain_age_days': -1,
                    'is_suspicious': False,
                    'message': f'{domain}은 주요 플랫폼으로 안전합니다.'
                }), 200

        # Whois 조회
        w = whois.whois(domain)

        creation_date = w.creation_date
        if isinstance(creation_date, list):
            creation_date = creation_date[0]

        if creation_date is None:
            return jsonify({
                'domain': domain,
                'creation_date': None,
                'domain_age_days': -1,
                'is_suspicious': True,
                'message': '도메인 생성일을 확인할 수 없습니다. 주의가 필요합니다.'
            }), 200

        # 날짜 계산
        if creation_date.tzinfo:
            now = datetime.now(timezone.utc)
        else:
            now = datetime.now()

        age_days = (now - creation_date).days
        is_suspicious = age_days < 30

        if is_suspicious:
            message = f'위험: 도메인이 {age_days}일 전에 생성되었습니다. 사기 사이트일 가능성이 높습니다.'
        else:
            message = f'도메인이 {age_days}일 전에 생성되었습니다. 안전한 도메인입니다.'

        print(f"[도메인 검사 결과] {domain}: {age_days}일, suspicious={is_suspicious}")

        return jsonify({
            'domain': domain,
            'creation_date': creation_date.strftime('%Y-%m-%d'),
            'domain_age_days': age_days,
            'is_suspicious': is_suspicious,
            'message': message
        }), 200

    except Exception as e:
        print(f"[도메인 검사 오류] {str(e)}")
        return jsonify({
            'domain': domain if 'domain' in dir() else 'unknown',
            'creation_date': None,
            'domain_age_days': -1,
            'is_suspicious': True,
            'message': f'도메인 검사 중 오류: {str(e)}'
        }), 200


@app.route('/analyze_video', methods=['POST'])
def analyze_video():
    try:
        data = request.get_json()
        if not data or 'url' not in data:
            return jsonify({'error': 'URL이 필요합니다'}), 400

        url = data['url']
        print(f"\n[요청 받음] URL: {url}")

        result = video_processor.process_video(url)
        print(f"[응답 전송] result={result['result']}, prob={result['probability']:.4f}")

        return jsonify(result), 200
    except Exception as e:
        error_msg = str(e)
        print(f"[오류 발생] {error_msg}")
        return jsonify({'error': error_msg}), 500


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)