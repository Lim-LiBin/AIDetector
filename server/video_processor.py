import cv2
import yt_dlp
import base64

class VideoProcessor:
    def __init__(self, model):
        """model은 ModelInference 인스턴스"""
        self.model = model
    
    def process_video(self, url):
        """
        영상 URL을 받아서 프레임별 분석 후 결과 반환
        """
        print(f"[영상 처리 시작] URL: {url}")
        
        # 1. yt-dlp로 스트리밍 URL 추출
        ydl_opts = {
            'format': 'best[height<=720]/best[height<=480]/best',
            'quiet': True,
            'no_warnings': True,
            'nocheckcertificate': True,
            'ignoreerrors': False,
        }
        
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                video_url = info['url']
        except Exception as e:
            print(f"[yt-dlp 오류] {str(e)}")
            raise Exception(f"영상 URL 추출 실패: {str(e)}")
        
        print("[스트리밍 URL 추출 완료]")
        
        # 2. OpenCV로 영상 열기
        cap = cv2.VideoCapture(video_url)
        
        if not cap.isOpened():
            raise Exception("영상을 열 수 없습니다")
        
        fps = int(cap.get(cv2.CAP_PROP_FPS))
        if fps == 0:
            fps = 30  # 기본값
        
        print(f"[영상 FPS] {fps}")
        
        # 3. 프레임 분석
        max_prob = 0.0
        best_frame = None
        best_heatmap = None
        frame_count = 0
        analyzed_count = 0
        
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            # 1초마다 1프레임만 분석
            if frame_count % fps == 0:
                analyzed_count += 1
                print(f"[분석 중] {analyzed_count}번째 프레임...")
                
                result = self.model.run_inference(frame)
                prob = result['score']
                heatmap = result['heatmap']
                
                if prob > max_prob:
                    max_prob = prob
                    best_frame = frame
                    best_heatmap = heatmap
            
            frame_count += 1
        
        cap.release()
        print(f"[분석 완료] 총 {analyzed_count}개 프레임 분석")
        print(f"[최고 확률] {max_prob:.4f}")
        
        if best_frame is None:
            raise Exception("분석 가능한 프레임이 없습니다")
        
        # 4. 결과 판별
        result = "Fake" if max_prob >= 0.5 else "Real"
        
        # 5. 프레임 이미지 압축
        height, width = best_frame.shape[:2]
        max_dimension = 640
        if max(height, width) > max_dimension:
            scale = max_dimension / max(height, width)
            new_width = int(width * scale)
            new_height = int(height * scale)
            best_frame = cv2.resize(best_frame, (new_width, new_height))
        
        # JPEG로 압축 (품질 50)
        encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), 50]
        _, buffer = cv2.imencode('.jpg', best_frame, encode_param)
        frame_base64 = base64.b64encode(buffer).decode('utf-8')
        
        # 6. 히트맵 변환
        heatmap_list = best_heatmap
        
        print(f"[응답 전송] result={result}, prob={max_prob:.4f}")
        
        return {
            "result": result,
            "probability": float(max_prob),
            "heatmap": heatmap_list,
            "frame": frame_base64
        }