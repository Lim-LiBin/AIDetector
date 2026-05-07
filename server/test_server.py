import requests
import json

# 테스트할 영상 URL (짧은 유튜브 영상으로 교체하세요)
test_url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

data = {
    "url": test_url
}

print(f"[테스트 시작] URL: {test_url}")
print("영상 분석 중... (시간이 걸릴 수 있습니다)\n")

try:
    response = requests.post(
        "http://localhost:5000/analyze_video",
        json=data,
        timeout=300
    )
    
    if response.status_code == 200:
        result = response.json()
        print(f"✅ [성공]")
        print(f"판별 결과: {result['result']}")
        print(f"확률: {result['probability']:.2%}")
        print(f"히트맵 크기: {len(result['heatmap'])}x{len(result['heatmap'][0])}")
        print(f"프레임 이미지: Base64 ({len(result['frame'])} 문자)")
    else:
        print(f"❌ [오류] {response.status_code}")
        print(response.json())
        
except Exception as e:
    print(f"❌ [예외 발생] {str(e)}")