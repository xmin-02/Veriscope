




test_sec.py

pkl 생성 :
& C:\Smart_IT\.venv\Scripts\python.exe C:\Smart_IT\test_sec.py build-index --workers 24 --embed-batch 256 --use-gpu --fp16 --http-pool 256 --sleep 0.05 --timeout 8 --fast-extract --

기사 (자료) 신뢰도 평가 :
# 🔍 Smart IT - AI 기반 신뢰도 평가 시스템

![Python](https://img.shields.io/badge/Python-3.13+-blue.svg)
![PyTorch](https://img.shields.io/badge/PyTorch-2.7+-red.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

**Smart IT**는 인공지능 기술을 활용하여 온라인 뉴스와 정보의 신뢰도를 다차원적으로 평가하는 고성능 시스템입니다.

## ✨ 주요 기능

### 🎯 핵심 기능
- **다차원 신뢰도 평가**: 출처, 내용, 시간성을 종합적으로 분석
- **실시간 가짜뉴스 탐지**: 24가지 패턴 기반 허위정보 식별
- **신뢰할 수 있는 출처 검증**: 238개 검증된 언론사/기관 데이터베이스
- **시간 가중 평가**: 오래된 정보에 대한 신뢰도 조정
- **고성능 병렬 처리**: Intel Ultra9 285k + RTX3070ti 최적화

### 🤖 AI 기술 스택
- **임베딩 모델**: `paraphrase-multilingual-MiniLM-L12-v2`
- **NLI 모델**: `cross-encoder/nli-deberta-v3-small`
- **벡터 검색**: FAISS 기반 고속 유사도 검색
- **텍스트 분석**: Transformer 기반 다국어 지원

### ⚡ 성능 최적화
- **하드웨어 가속**: CUDA GPU + Intel OpenVINO
- **프로듀서-컨슈머 아키텍처**: CPU 크롤링 + GPU 임베딩 분리
- **동적 배치 처리**: GPU 메모리에 따른 자동 배치 크기 조정
- **병렬 크롤링**: 최대 48개 워커를 통한 고속 데이터 수집

## 🚀 빠른 시작

### 📋 시스템 요구사항

**최소 요구사항:**
- Python 3.10+
- RAM 8GB+
- 저장공간 5GB+

**권장 사양:**
- Python 3.13+
- Intel/AMD CPU (12코어+)
- NVIDIA GPU (RTX3060+, VRAM 6GB+)
- RAM 32GB+
- SSD 저장공간 10GB+

### 🛠️ 설치 방법

#### 1. 저장소 클론
```bash
git clone https://github.com/your-repo/Smart_IT.git
cd Smart_IT
```

#### 2. 가상환경 생성 및 활성화
```bash
# Windows
python -m venv .venv
.venv\Scripts\activate

# Linux/macOS
python -m venv .venv
source .venv/bin/activate
```

#### 3. 의존성 설치
```bash
# CPU 버전 (기본)
pip install -r requirements.txt

# GPU 버전 (NVIDIA CUDA)
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
pip install -r requirements.txt
```

#### 4. NLTK 데이터 다운로드
```bash
python -c "import nltk; nltk.download('punkt')"
```

## 📖 사용 방법

### 🏗️ 1단계: 인덱스 빌드

신뢰도 평가를 위한 기준 데이터베이스를 구축합니다.

#### 테스트 모드 (빠른 테스트)
```bash
python test_sec.py build-index --test-mode --use-gpu --fast-extract
```

#### 전체 모드 (238개 시드 전체)
```bash
python test_sec.py build-index --workers 24 --embed-batch 1024 --use-gpu --fast-extract
```

#### 주요 옵션 설명
- `--workers N`: 병렬 크롤링 워커 수 (CPU 코어 수에 맞춰 조정)
- `--embed-batch N`: GPU 임베딩 배치 크기 (VRAM에 맞춰 조정)
- `--use-gpu`: CUDA GPU 가속 활성화
- `--fp16`: FP16 정밀도로 메모리 절약
- `--fast-extract`: 빠른 텍스트 추출 (정확도 < 속도)
- `--test-mode`: 소규모 테스트 (3개 시드만 사용)

### 🔍 2단계: 신뢰도 평가

구축된 인덱스를 사용하여 특정 URL의 신뢰도를 평가합니다.

```bash
# 기본 평가
python test_sec.py evaluate --url "https://news.example.com/article/123"

# GPU 가속 + 상세 출력
python test_sec.py evaluate --url "https://news.example.com/article/123" --use-gpu --fp16 --verbose
```

### 🌐 3단계: Flask API 서버 (선택사항)

웹 서비스나 다른 애플리케이션에서 API를 통해 신뢰도 평가 기능을 사용할 수 있습니다.

#### API 서버 시작
```bash
python flask_api.py
```

#### API 사용 예시

**PowerShell:**
```powershell
Invoke-WebRequest -Uri "http://localhost:5004/evaluate" -Method POST -Headers @{"Content-Type"="application/json"} -Body '{"url": "https://n.news.naver.com/mnews/article/003/0013551967", "similarity_threshold": 0.6}'
```

**curl (Git Bash/Linux):**
```bash
curl -X POST http://localhost:5004/evaluate -H "Content-Type: application/json" -d '{"url": "https://n.news.naver.com/mnews/article/003/0013551967", "similarity_threshold": 0.6}'
```

**Python:**
```python
import requests
response = requests.post("http://localhost:5004/evaluate", json={
    "url": "https://n.news.naver.com/mnews/article/003/0013551967",
    "similarity_threshold": 0.6
})
result = response.json()
```

#### API 응답 형식
```json
{
  "success": true,
  "elapsed_seconds": 12.2,
  "result": {
    "reliability_score": 81,
    "reliability_level": "매우 높음",
    "evidence_count": 5,
    "evidence": [
      {
        "rank": 1,
        "reliability_score": 72,
        "url": "https://www.mk.co.kr/news/economy/11449838",
        "similarity": 0.76,
        "support": 0.56
      }
    ],
    "recommendation": "이 기사는 신뢰할 만합니다."
  }
}
```

### 📊 출력 예시

```
🎯 최종 평가 결과
==================================================
신뢰도: 87% - 매우 높음
권장사항: 신뢰할 수 있는 정보로 판단됩니다.

📋 신뢰도 해석 가이드
• 80% 이상: 매우 높음 - 신뢰 가능
• 65-79%: 높음 - 대체로 신뢰 가능, 추가 검증 권장  
• 50-64%: 보통 - 신중한 검토 필요
• 35-49%: 낮음 - 다른 출처 확인 필요
• 35% 미만: 매우 낮음 - 허위정보 의심
```

## 🔧 고급 설정

### ⚙️ 성능 최적화

#### Intel CPU 최적화
```bash
# OpenVINO 백엔드 활성화
export OPENVINO_BACKEND=1
python test_sec.py build-index --workers 32 --fp16
```

#### NVIDIA GPU 최적화
```bash
# 고성능 GPU 설정
python test_sec.py build-index --embed-batch 2048 --use-gpu --fp16
```

#### 메모리 최적화
```bash
# 대용량 메모리 활용
python test_sec.py build-index --http-pool 2048 --workers 48
```

### 📁 프로젝트 구조

```
Smart_IT/
├── 📄 test_sec.py              # 메인 실행 파일
├── 📄 enhanced_seed_links.csv  # 검증된 시드 링크 (238개)
├── 📄 requirements.txt         # 의존성 패키지
├── 📄 README.md               # 프로젝트 문서
├── 📄 performance_test.py      # 성능 벤치마크
├── 📄 resource_monitor.py      # 하드웨어 모니터링
├── 📄 fake_news_test.html     # 테스트용 가짜뉴스
├── 📂 __pycache__/           # Python 캐시
├── 📂 ov_cache/              # OpenVINO 캐시
└── 📂 ov_ir/                 # OpenVINO IR 모델
```

### 🗂️ 데이터 소스

**enhanced_seed_links.csv**에는 다음과 같은 신뢰할 수 있는 출처들이 포함되어 있습니다:

- **국내 언론사**: KBS, MBC, SBS, 연합뉴스, 한겨레, 조선일보 등
- **해외 언론사**: BBC, Reuters, CNN, AP News, NPR 등  
- **정부기관**: 청와대, 외교부, 보건복지부, 통계청 등
- **국제기구**: UN, WHO, IMF, World Bank 등
- **팩트체킹**: 팩트체크넷, PolitiFact, Snopes 등

## 🧪 테스트 및 검증

### 📈 성능 벤치마크
```bash
python performance_test.py
```

### 🔍 하드웨어 모니터링
```bash
python resource_monitor.py
```

### 🧾 알려진 테스트 케이스

```bash
# 신뢰할 수 있는 뉴스 (80%+)
python test_sec.py evaluate --url "https://news.kbs.co.kr/news/view.do?ncd=5678901"

# 오래된/의심스러운 정보 (50% 이하)
python test_sec.py evaluate --url "https://news.jtbc.co.kr/article/NB11272032"
```

## 🛠️ 문제 해결

### 일반적인 문제들

#### GPU 메모리 부족
```bash
# 배치 크기 줄이기
python test_sec.py build-index --embed-batch 512 --use-gpu
```

#### 크롤링 타임아웃
```bash
# 타임아웃 늘리기
python test_sec.py build-index --timeout 30 --sleep 0.1
```

#### 메모리 부족
```bash
# 워커 수 줄이기
python test_sec.py build-index --workers 8 --http-pool 512
```

### 로그 및 디버깅

```bash
# 상세 로그
python test_sec.py build-index --verbose --log-file build.log

# 간단 로그
python test_sec.py build-index --quiet
```

## 📊 성능 지표

### 벤치마크 결과 (Intel Ultra9 285k + RTX3070ti)

| 작업 | 처리량 | 소요시간 |
|------|--------|----------|
| 임베딩 (배치 512) | 1,410개/초 | 최적 |
| 병렬 크롤링 (48워커) | 243작업/초 | 고속 |
| 테스트 빌드 (3시드) | 1,800+ 청크 | 1-2분 |
| 전체 빌드 (238시드) | 50,000+ 청크 | 15-30분 |

## 🤝 기여하기

1. 이 저장소를 포크합니다
2. 새로운 기능 브랜치를 만듭니다 (`git checkout -b feature/AmazingFeature`)
3. 변경사항을 커밋합니다 (`git commit -m 'Add some AmazingFeature'`)
4. 브랜치에 푸시합니다 (`git push origin feature/AmazingFeature`)
5. Pull Request를 열어주세요

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 자세한 내용은 `LICENSE` 파일을 참조하세요.

## 🙏 감사의 글

- [Hugging Face](https://huggingface.co/) - 사전 훈련된 언어 모델
- [Sentence Transformers](https://www.sbert.net/) - 텍스트 임베딩
- [FAISS](https://github.com/facebookresearch/faiss) - 고속 벡터 검색
- [Intel OpenVINO](https://openvino.ai/) - CPU 최적화
- [PyTorch](https://pytorch.org/) - 딥러닝 프레임워크

## 📞 지원 및 연락

문제나 질문이 있으시면 GitHub Issues를 통해 문의해 주세요.

---

**Smart IT** - AI로 더 신뢰할 수 있는 정보 생태계를 만들어갑니다. 🌟


curl -X POST http://localhost:5002/evaluate -H "Content-Type: application/json" -d '{"url": "https://n.news.naver.com/mnews/article/003/0013551967", "similarity_threshold": 0.6}'

Invoke-WebRequest -Uri "http://localhost:5002/evaluate" -Method POST -Headers @{"Content-Type"="application/json"} -Body '{"url": "https://n.news.naver.com/mnews/article/003/0013551967", "similarity_threshold": 0.6}'#   V e r i s c o p e  
 #   V e r i s c o p e  
 