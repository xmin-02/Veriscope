# 🔍 Veriscope - AI 기반 뉴스 신뢰도 평가 시스템

![Python](https://img.shields.io/badge/Python-3.13+-blue.svg)
![PyTorch](https://img.shields.io/badge/PyTorch-2.7+-red.svg)
![Android](https://img.shields.io/badge/Android-Kotlin-green.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

**Veriscope**는 인공지능 기술을 활용하여 온라인 뉴스와 정보의 신뢰도를 다차원적으로 평가하는 시스템입니다.  
Python 기반 백엔드 서버와 Android 앱으로 구성되어 있습니다.

---

## ✨ 주요 기능

### 🎯 핵심 기능
- **다차원 신뢰도 평가** — 출처, 내용, 시간성을 종합 분석
- **실시간 가짜뉴스 탐지** — 24가지 패턴 기반 허위정보 식별
- **이미지 분석** — OCR + AI 기반 이미지 내 텍스트 신뢰도 평가
- **신뢰할 수 있는 출처 검증** — 238개 검증된 언론사/기관 데이터베이스
- **Android 앱** — 모바일에서 URL/이미지로 즉시 신뢰도 확인

### 🤖 AI 기술 스택
- **임베딩 모델**: `paraphrase-multilingual-MiniLM-L12-v2`
- **NLI 모델**: `cross-encoder/nli-deberta-v3-small`
- **벡터 검색**: FAISS 기반 고속 유사도 검색
- **하드웨어 가속**: CUDA GPU + Intel OpenVINO

---

## 📁 프로젝트 구조

```
Veriscope/
├── Veriscope.py              # 메인 엔진 (신뢰도 분석 코어)
├── Veriscope_api.py          # 통합 API 서버 (인증 + 평가, 포트 5004)
├── veriscope_api_server.py   # 인증 API 서버
├── veriscope_unified_api.py  # 통합 API
├── Veriscope_img.py          # 이미지 분석 모듈
├── app.py                    # 앱 엔트리포인트
├── create_database.py        # DB 생성 스크립트
├── requirements.txt          # Python 의존성
├── README.md
│
├── server/                   # PHP 백엔드 서버
│   ├── auth/                 #   로그인/회원가입
│   └── config/               #   DB 설정
│
├── database/                 # DB 스키마
│   ├── veriscope_schema.sql
│   └── add_phone_column.sql
│
└── Smart_it/                 # Android 앱 (Kotlin)
    ├── app/src/main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/example/veriscope/
    │   └── res/
    ├── build.gradle.kts
    └── settings.gradle.kts
```

---

## 🚀 빠른 시작

### 📋 시스템 요구사항

| 항목 | 최소 | 권장 |
|------|------|------|
| Python | 3.10+ | 3.13+ |
| RAM | 8GB | 32GB |
| GPU | — | NVIDIA RTX3060+ (VRAM 6GB+) |
| 저장공간 | 5GB | 10GB (SSD) |

### 🛠️ 설치

```bash
# 1. 저장소 클론
git clone https://github.com/xmin-02/Veriscope.git
cd Veriscope

# 2. 가상환경 생성
python -m venv .venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # Linux/macOS

# 3. 의존성 설치
pip install -r requirements.txt

# 4. GPU 지원 (선택)
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118

# 5. NLTK 데이터
python -c "import nltk; nltk.download('punkt')"
```

### ▶️ 실행

#### 인덱스 빌드
```bash
# 테스트 모드 (빠른 테스트)
python Veriscope.py build-index --test-mode --use-gpu --fast-extract

# 전체 빌드 (238개 시드)
python Veriscope.py build-index --workers 24 --embed-batch 1024 --use-gpu --fast-extract
```

#### 신뢰도 평가
```bash
python Veriscope.py evaluate --url "https://news.example.com/article/123" --use-gpu
```

#### API 서버 시작
```bash
python Veriscope_api.py
# 서버 주소: http://localhost:5004
```

---

## 🌐 API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/health` | 서버 상태 확인 |
| `GET` | `/` | API 정보 |
| `POST` | `/auth/login` | 로그인 |
| `POST` | `/auth/signup` | 회원가입 |
| `POST` | `/auth/forgot-password` | 비밀번호 찾기 |
| `POST` | `/evaluate` | URL 신뢰도 평가 |
| `POST` | `/evaluate-image` | 이미지 신뢰도 평가 |

### 사용 예시

```bash
# URL 평가
curl -X POST http://localhost:5004/evaluate \
  -H "Content-Type: application/json" \
  -d '{"url": "https://n.news.naver.com/mnews/article/003/0013551967"}'
```

### 응답 예시
```json
{
  "success": true,
  "elapsed_seconds": 12.2,
  "result": {
    "reliability_score": 81,
    "reliability_level": "매우 높음",
    "evidence_count": 5,
    "recommendation": "이 기사는 신뢰할 만합니다."
  }
}
```

---

## 📊 신뢰도 해석 가이드

| 점수 | 등급 | 의미 |
|------|------|------|
| 80% 이상 | 매우 높음 | 신뢰 가능 |
| 65-79% | 높음 | 대체로 신뢰 가능, 추가 검증 권장 |
| 50-64% | 보통 | 신중한 검토 필요 |
| 35-49% | 낮음 | 다른 출처 확인 필요 |
| 35% 미만 | 매우 낮음 | 허위정보 의심 |

---

## 📱 Android 앱

`Smart_it/` 폴더에 Kotlin 기반 Android 앱이 포함되어 있습니다.

- **Android Studio**에서 `Smart_it/` 폴더를 열어 빌드
- API 서버 주소를 `ApiClient.kt`에서 설정
- 기능: URL 입력 → 신뢰도 평가, 이미지 촬영 → OCR 분석

---

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.
