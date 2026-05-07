import tensorflow as tf
import numpy as np
import cv2
from PIL import Image

class ModelInference:
    def __init__(self, model_path='model.tflite'):
        """TFLite 모델 로드"""
        self.interpreter = tf.lite.Interpreter(model_path=model_path)
        self.interpreter.allocate_tensors()
        
        self.input_details = self.interpreter.get_input_details()
        self.output_details = self.interpreter.get_output_details()
        
        print(f"[모델 로드 완료]")
        print(f"입력 형태: {self.input_details[0]['shape']}")
        print(f"출력 개수: {len(self.output_details)}")
    
    def preprocess_image(self, image):
        """
        이미지 전처리
        AiProcessor.java의 processImage()와 동일한 로직
        """
        # PIL Image 또는 numpy array를 받음
        if isinstance(image, Image.Image):
            image = np.array(image)
        
        # OpenCV는 BGR이므로 RGB로 변환
        if len(image.shape) == 3 and image.shape[2] == 3:
            image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        
        # 224x224로 리사이즈 (BILINEAR)
        image_resized = cv2.resize(image, (224, 224), interpolation=cv2.INTER_LINEAR)
        
        # 0.0 ~ 1.0 정규화 (NormalizeOp(0.0f, 255.0f)와 동일)
        image_normalized = image_resized.astype(np.float32) / 255.0
        
        # 배치 차원 추가 [1, 224, 224, 3]
        input_data = np.expand_dims(image_normalized, axis=0)
        
        return input_data
    
    def run_inference(self, image):
        """
        추론 실행
        AiProcessor.java의 runInference()와 동일한 로직
        
        Returns:
            dict: {'score': float, 'heatmap': list (7x7)}
        """
        try:
            # 전처리
            input_data = self.preprocess_image(image)
            
            # 추론 실행
            self.interpreter.set_tensor(self.input_details[0]['index'], input_data)
            self.interpreter.invoke()
            
            # 출력 추출
            # output 0: heatmap [1, 7, 7, 1280]
            # output 1: probability [1, 1]
            heatmap_raw = self.interpreter.get_tensor(self.output_details[0]['index'])
            probability = self.interpreter.get_tensor(self.output_details[1]['index'])
            
            # 확률값 추출
            final_score = float(probability[0][0])
            
            # 히트맵 가공: 1280개 채널의 평균값 구하기
            # heatmap_raw shape: [1, 7, 7, 1280]
            final_heatmap = np.mean(heatmap_raw[0], axis=-1)  # [7, 7]
            
            # Python list로 변환 (JSON 직렬화를 위해)
            final_heatmap = final_heatmap.tolist()
            
            return {
                'score': final_score,
                'heatmap': final_heatmap
            }
            
        except Exception as e:
            print(f"[추론 오류] {str(e)}")
            raise e