import cv2
import yt_dlp
import base64
import os

class VideoProcessor:
    def __init__(self, model):
        self.model = model

        base_path = os.path.dirname(os.path.abspath(__file__))
        xml_path = os.path.join(base_path, 'haarcascade_frontalface_default.xml')

        if not os.path.exists(xml_path):
            raise Exception(f"얼굴 검출기 XML 파일을 찾을 수 없습니다: {xml_path}")

        self.face_cascade = cv2.CascadeClassifier(xml_path)

    def process_video(self, url):
        print(f"[영상 처리 시작] URL: {url}")

        ydl_opts = {
            'format': 'best',
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

        cap = cv2.VideoCapture(video_url)

        if not cap.isOpened():
            raise Exception("영상을 열 수 없습니다")

        fps = int(cap.get(cv2.CAP_PROP_FPS))
        if fps == 0:
            fps = 30

        print(f"[영상 FPS] {fps}")

        max_prob = 0.0
        best_frame = None
        best_heatmap = None
        best_face_coords = None
        frame_count = 0
        analyzed_count = 0

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            if frame_count % fps == 0:
                analyzed_count += 1
                print(f"[분석 중] {analyzed_count}번째 프레임...")

                gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                faces = self.face_cascade.detectMultiScale(gray, 1.1, 5, minSize=(50, 50))

                face_coords = None
                if len(faces) > 0:
                    x, y, w, h = max(faces, key=lambda f: f[2]*f[3])
                    pw, ph = int(w*0.2), int(h*0.2)
                    x1, y1 = max(x-pw, 0), max(y-ph, 0)
                    x2, y2 = min(x+w+pw, frame.shape[1]), min(y+h+ph, frame.shape[0])
                    face_img = frame[y1:y2, x1:x2]
                    result = self.model.run_inference(face_img)
                    face_coords = {
                        "x1": int(x1),
                        "y1": int(y1),
                        "cropW": int(x2 - x1),
                        "cropH": int(y2 - y1)
                    }
                else:
                    result = self.model.run_inference(frame)

                prob = result['score']
                heatmap = result['heatmap']

                if prob > max_prob:
                    max_prob = prob
                    best_frame = frame
                    best_heatmap = heatmap
                    best_face_coords = face_coords

            frame_count += 1

        cap.release()
        print(f"[분석 완료] 총 {analyzed_count}개 프레임 분석")
        print(f"[최고 확률] {max_prob:.4f}")

        if best_frame is None:
            raise Exception("분석 가능한 프레임이 없습니다")

        result = "Fake" if max_prob >= 0.5 else "Real"

        height, width = best_frame.shape[:2]
        max_dimension = 640
        scale = 1.0
        if max(height, width) > max_dimension:
            scale = max_dimension / max(height, width)
            new_width = int(width * scale)
            new_height = int(height * scale)
            best_frame = cv2.resize(best_frame, (new_width, new_height))

        # 프레임 리사이즈했으면 좌표도 스케일 맞춤
        if best_face_coords and scale != 1.0:
            best_face_coords = {
                "x1": int(best_face_coords["x1"] * scale),
                "y1": int(best_face_coords["y1"] * scale),
                "cropW": int(best_face_coords["cropW"] * scale),
                "cropH": int(best_face_coords["cropH"] * scale)
            }

        encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), 50]
        _, buffer = cv2.imencode('.jpg', best_frame, encode_param)
        frame_base64 = base64.b64encode(buffer).decode('utf-8')

        heatmap_list = best_heatmap

        print(f"[응답 전송] result={result}, prob={max_prob:.4f}, face_coords={best_face_coords}")

        return {
            "result": result,
            "probability": float(max_prob),
            "heatmap": heatmap_list,
            "frame": frame_base64,
            "face_coords": best_face_coords
        }