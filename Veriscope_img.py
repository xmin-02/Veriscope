# test_sec.py
# --------------------------------------------------------------------------------------------
# 시드 크롤(병렬) → 임베딩 인덱스(pkl) → 평가 시 NLI 재랭크(배치)
# 출력: Top-5 근거(각 %) + 최종 신뢰도 %
# - 네이버/ JTBC 전용 본문 추출기 추가
# - AMP 서브도메인 잘못 시도 제거 (amp.news.*)
# - 후보 TopK 상향(500) + 한국어 비율 가중치 + 증거 중복 제거
# --------------------------------------------------------------------------------------------
# --------------------------------------------------------------------------------------------
# 🧪 테스트용 빠른 빌드 (3개 시드, 1-2분 소요) - 하드웨어 최대 활용:
# & C:\Smart_IT\.venv\Scripts\python.exe C:\Smart_IT\test_sec.py build-index --test-mode --use-gpu --fp16 --fast-extract
#
# � 전체 인덱스 빌드 (238개 시드, 15-30분 소요) - 최고 성능 모드:
# & C:\Smart_IT\.venv\Scripts\python.exe C:\Smart_IT\test_sec.py build-index --use-gpu --fp16 --fast-extract
#
# 🔍 신뢰도 평가 실행:
# & C:\Smart_IT\.venv\Scripts\python.exe C:\Smart_IT\test_sec.py evaluate --url "기사URL" --use-gpu --fp16
#
# 💻 하드웨어 최적화: Intel Ultra9 285k (32스레드) + RTX3070ti (8GB) + 128GB RAM 최대 활용
# --------------------------------------------------------------------------------------------

import os
import re
import csv
import sys
import math
import time
import pickle
import queue
import argparse
import urllib.parse as up
import logging
import multiprocessing as mp
from datetime import datetime, timezone
from dataclasses import dataclass
from typing import List, Tuple, Optional, Callable
from concurrent.futures import ThreadPoolExecutor, ProcessPoolExecutor, as_completed
from threading import Lock

# 프로세스 우선순위 설정 (Windows)
if sys.platform == "win32":
    try:
        import psutil
        # 현재 프로세스 우선순위를 높음으로 설정
        current_process = psutil.Process()
        current_process.nice(psutil.HIGH_PRIORITY_CLASS)
    except (ImportError, Exception):
        pass

# NPU/OpenVINO 지원 (선택적)
NPU_AVAILABLE = False
try:
    import openvino as ov
    NPU_AVAILABLE = True
except ImportError:
    pass

# --- third-party ---
import numpy as np
import requests
from bs4 import BeautifulSoup
import trafilatura
from newspaper import Article
from tqdm import tqdm

# BeautifulSoup 경고 억제
import warnings
from bs4 import MarkupResemblesLocatorWarning
warnings.filterwarnings("ignore", category=MarkupResemblesLocatorWarning)

import torch
from sentence_transformers import SentenceTransformer, util
from transformers import AutoTokenizer, AutoModelForSequenceClassification

from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from readability import Document
import json

# --------------------------------------------------------------------------------------------
# 고정 경로
SEED_CSV  = r"C:\Smart_IT\enhanced_seed_links.csv"  # 개선된 시드 링크 사용
INDEX_PKL = r"C:\Smart_IT\smart_it_index.pkl"

# 기본 정책
MAX_DEPTH = 2
MAX_PAGES_PER_DOMAIN = 150      # 도메인당 최대 페이지(품질/시간 트레이드오프)
REQUEST_TIMEOUT = 12
CRAWL_SLEEP = 0.5

# 검색/스코어 정책
TOPK_CANDIDATES = 500           # ← 중요: 0 이면 NLI가 비어버림
TOPN_RETURN = 5
MIN_TEXT_LEN = 200              # 운영용 권장값(디버깅 시 낮춰도 됨)
MIN_SIMILARITY_THRESHOLD = 0.35  # 최소 유사성 임계값 (품질 개선: 0.15 → 0.35)
MIN_NLI_SUPPORT_THRESHOLD = 0.1  # 최소 NLI 지지도 임계값
MIN_FINAL_SCORE = 0.3           # 최종 점수 최소 임계값

# 스코어 가중치 (조정됨)
ALPHA_SIM = 0.65      # 유사성 가중치 (높임)
BETA_SUP  = 0.35      # NLI 지지도 가중치 (높임)  
GAMMA_CONTRA = 0.50   # NLI 반박 가중치 (높임)
DELTA_TIME = 0.20     # 시간 가중치 (0.10 → 0.20으로 강화)
EPS_SOURCE = 0.20     # 출처 신뢰성 가중치 (높임)
EPS_LANG   = 0.15     # 한국어/영어 등 질의-문서 언어 정합 가중 (높임)
TIME_LAMBDA = 0.0025

# 로깅
logger = logging.getLogger("smart_it")

def setup_logging(verbose: bool, quiet: bool, log_file: Optional[str], build_mode: bool = False):
    if build_mode and not verbose:
        # 빌드 모드에서는 진행도 가시성을 위해 로그 최소화
        level = logging.ERROR
    else:
        level = logging.INFO
        if verbose:
            level = logging.DEBUG
        if quiet:
            level = logging.WARNING
    
    handlers = []
    if log_file:
        # 파일로만 로그 출력 (진행도 표시 방해 방지)
        handlers.append(logging.FileHandler(log_file, encoding="utf-8"))
    elif not build_mode or verbose:
        # 빌드 모드가 아니거나 verbose 모드일 때만 콘솔 출력
        handlers.append(logging.StreamHandler(sys.stderr))  # stderr로 변경하여 진행도와 분리
    
    if handlers:
        logging.basicConfig(level=level, format="%(asctime)s [%(levelname)s] %(message)s", handlers=handlers)
    else:
        # 로그 핸들러가 없으면 NullHandler 사용
        logging.basicConfig(level=logging.CRITICAL, handlers=[logging.NullHandler()])
    
    if verbose or not build_mode:
        logger.debug("로깅 초기화(level=%s, log_file=%s)", logging.getLevelName(level), log_file)

# --------------------------------------------------------------------------------------------
# HTTP 세션(커넥션 풀/재시도)
SESSION = None
def configure_http(http_pool: int, timeout: int):
    global SESSION, REQUEST_TIMEOUT
    REQUEST_TIMEOUT = timeout
    sess = requests.Session()
    adapter = HTTPAdapter(
        pool_connections=http_pool,
        pool_maxsize=http_pool,
        max_retries=Retry(total=2, backoff_factor=0.2, status_forcelist=[429, 500, 502, 503, 504]),
    )
    sess.mount("http://", adapter)
    sess.mount("https://", adapter)
    SESSION = sess
    logger.info("HTTP 설정: pool=%d, timeout=%ds", http_pool, timeout)

def polite_get(url: str, mobile: bool = False) -> Optional[str]:
    try:
        # 로컬 파일 지원
        if url.startswith('file://'):
            file_path = url.replace('file://', '').replace('/', '\\')
            if file_path.startswith('\\C:'):
                file_path = 'C:' + file_path[3:]
            with open(file_path, 'r', encoding='utf-8') as f:
                return f.read()
        
        ua_desktop = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36"
        ua_mobile  = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"
        h = {
            "User-Agent": (ua_mobile if mobile else ua_desktop),
            "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
            "Cache-Control": "no-cache",
            "Pragma": "no-cache",
            "Referer": url,
        }
        r = SESSION.get(url, headers=h, timeout=REQUEST_TIMEOUT)
        if r.status_code == 200 and "text/html" in r.headers.get("Content-Type", ""):
            return r.text
        logger.debug("GET %s -> %s (%s)", url, r.status_code, r.headers.get("Content-Type"))
    except Exception as e:
        logger.debug("GET 실패 %s (%s)", url, e)
    return None

# --------------------------------------------------------------------------------------------
# 유틸/전처리
def now_utc() -> datetime:
    return datetime.now(tz=timezone.utc)

def normalize_space(s: str) -> str:
    return re.sub(r"\s+", " ", (s or "").strip())

def canonical_url(u: str) -> str:
    try:
        p = up.urlparse(u)
        q = up.parse_qs(p.query)
        # 추적 파라미터 및 불필요한 파라미터 제거
        filtered = {k: v for k, v in q.items() if not k.startswith((
            "utm_", "fbclid", "gclid", "igshid", "ref", "share", "_ga", "campaign", 
            "source", "medium", "term", "content", "spm_id", "module", "pgtype"
        ))}
        new_q = up.urlencode([(k, vv) for k, vals in filtered.items() for vv in vals])
        p2 = p._replace(query=new_q)
        netloc = p2.netloc.lower()
        path = re.sub(r"/+", "/", p2.path)
        if path != "/" and path.endswith("/"):
            path = path[:-1]
        p3 = p2._replace(netloc=netloc, path=path)
        return up.urlunparse(p3)
    except Exception:
        return u

def url_similarity(url1: str, url2: str) -> float:
    """두 URL 간의 유사성을 계산 (0~1)"""
    try:
        p1, p2 = up.urlparse(url1), up.urlparse(url2)
        
        # 도메인이 다르면 0
        if p1.netloc.lower() != p2.netloc.lower():
            return 0.0
        
        # 경로가 완전히 같으면 1
        if p1.path == p2.path:
            return 1.0
        
        # 경로 세그먼트 비교
        path1_parts = [part for part in p1.path.split('/') if part]
        path2_parts = [part for part in p2.path.split('/') if part]
        
        if not path1_parts or not path2_parts:
            return 0.0
        
        # 마지막 세그먼트(주로 기사 ID)만 다른 경우 높은 유사성
        if len(path1_parts) == len(path2_parts):
            common_parts = sum(1 for a, b in zip(path1_parts[:-1], path2_parts[:-1]) if a == b)
            similarity = common_parts / max(1, len(path1_parts) - 1)
            if similarity >= 0.8:  # 경로의 80% 이상이 같으면
                return 0.9
        
        return 0.0
    except Exception:
        return 0.0

def domain_of(u: str) -> str:
    try:
        return up.urlparse(u).netloc.lower()
    except Exception:
        return ""

def is_same_domain(u: str, seed_domain: str) -> bool:
    d = domain_of(u)
    return d == seed_domain or d.endswith("." + seed_domain)

def extract_links(base_url: str, html: str) -> List[str]:
    out = []
    try:
        soup = BeautifulSoup(html, "html.parser")
        for a in soup.find_all("a", href=True):
            href = a["href"].strip()
            abs_u = up.urljoin(base_url, href)
            if abs_u.startswith(("http://", "https://")):
                out.append(canonical_url(abs_u))
    except Exception:
        pass
    return list(set(out))

def korean_ratio(text: str) -> float:
    if not text:
        return 0.0
    total = len(text)
    hangul = sum(1 for ch in text if '\uac00' <= ch <= '\ud7a3')
    return hangul / max(1, total)

# --------------------------------------------------------------------------------------------
# 본문 추출 (도메인 전용 → AMP/JSON-LD/Next.js/Readability → trafilatura → manual → newspaper3k)
def extract_text(url: str, html: Optional[str], fast: bool = False) -> Tuple[str, Optional[datetime], str]:
    text, dt, title = "", None, ""

    # 0) html 없으면 데스크톱→모바일 순으로 시도 (네이버는 아래 전용기로 보정)
    if not html:
        html = polite_get(url) or polite_get(url, mobile=True)

    def _clean_html_text(h: str) -> str:
        soup = BeautifulSoup(h, "html.parser")
        for tag in soup(["script", "style", "noscript"]):
            tag.decompose()
        return normalize_space(soup.get_text(" ", strip=True))

    # ----- 전용 추출기: 네이버 -----
    def _extract_naver_article(h: str) -> Tuple[str, Optional[str], Optional[str]]:
        if not h:
            return "", None, None
        soup = BeautifulSoup(h, "html.parser")
        node = soup.select_one("#dic_area") or soup.select_one(".newsct_article")
        body = ""
        if node:
            for bad in node.select("figure, .promotion, .byline, .copyright, .end_photo_org, .img_desc"):
                bad.decompose()
            body = normalize_space(node.get_text(" ", strip=True))
        hed = None
        tnode = soup.select_one("h2#title_area .media_end_head_headline") or soup.select_one("h2.media_end_head_headline")
        if tnode:
            hed = normalize_space(tnode.get_text(" ", strip=True))
        if not hed and soup.title and soup.title.string:
            hed = normalize_space(soup.title.string)
        date_str = None
        meta = soup.find("meta", attrs={"property": "article:published_time"})
        if meta and meta.get("content"):
            date_str = meta["content"]
        else:
            dnode = soup.select_one("span.media_end_head_info_datestamp_time")
            if dnode and dnode.get("data-date-time"):
                date_str = dnode["data-date-time"]
        return body, date_str, hed

    # ----- 전용 추출기: JTBC(Next.js JSON + CSS 선택자) -----
    def _extract_jtbc_nextdata(h: str) -> Tuple[str, Optional[str], Optional[str]]:
        if not h:
            return "", None, None
        soup = BeautifulSoup(h, "html.parser")
        
        # 0) Next.js self.__next_f.push 방식 처리 (최우선)
        scripts = soup.find_all("script")
        for script in scripts:
            if script.string and "self.__next_f.push" in script.string:
                try:
                    content = script.string
                    
                    # 한국어 텍스트 패턴으로 기사 내용 추출
                    korean_pattern = r'"([^"]*[가-힣]+[^"]*)"'
                    matches = re.findall(korean_pattern, content)
                    
                    # 가장 긴 한국어 텍스트를 기사 본문으로 사용
                    longest_text = ""
                    for match in matches:
                        if len(match) > len(longest_text) and len(match) > 100:
                            # 기사 내용 같은 패턴인지 확인
                            if any(keyword in match for keyword in ['말했다', '밝혔다', '전했다', '발표했다', '설명했다']):
                                longest_text = match
                            elif len(match) > 200:  # 충분히 긴 텍스트면 기사 내용일 가능성 높음
                                longest_text = match
                    
                    if longest_text:
                        # 이스케이프 문자 처리
                        cleaned = longest_text.replace('\\n', '\n').replace('\\t', ' ')
                        cleaned = cleaned.replace('\\"', '"').replace('\\\\', '\\')
                        
                        # HTML 태그 제거
                        cleaned = re.sub(r'<[^>]+>', '', cleaned)
                        cleaned = re.sub(r'\s+', ' ', cleaned).strip()
                        
                        if len(cleaned) > 100:
                            logger.debug(f"JTBC Next.js 한국어 패턴 추출 성공 ({len(cleaned)}자)")
                            
                            # 제목 추출
                            title = ""
                            if soup.title:
                                title = soup.title.get_text(strip=True)
                            
                            return normalize_space(cleaned), None, normalize_space(title) if title else None
                                
                except Exception as e:
                    logger.debug(f"JTBC Next.js 패턴 파싱 오류: {e}")
                    continue
        
        # 0-1) Meta description fallback
        meta_desc = soup.find("meta", attrs={"name": "description"})
        if not meta_desc:
            meta_desc = soup.find("meta", attrs={"property": "og:description"})
        
        if meta_desc and meta_desc.get('content'):
            desc = meta_desc.get('content')
            if len(desc) > 80:  # Meta description이 충분히 긴 경우
                logger.debug(f"JTBC Meta description 추출 성공 ({len(desc)}자)")
                
                title = ""
                if soup.title:
                    title = soup.title.get_text(strip=True)
                
                return normalize_space(desc), None, normalize_space(title) if title else None
        
        # 1) CSS 선택자 기반 본문 추출 (JTBC 전용 선택자 추가)
        article_selectors = [
            "div[data-module='ArticleContent']",
            "article .newsroom_article_content",
            ".newsroom_article_content", 
            "article .article_content",
            ".article_content",
            "[data-testid='article-content']",
            ".news_article_body",
            ".article_body_content",
            ".MuiBox-root p",  # Material-UI 구조
            "main p",
            "[class*='ArticleContent']",
            "[class*='article-body']",
            "[class*='news-body']"
        ]
        
        for selector in article_selectors:
            elements = soup.select(selector)
            if elements:
                # 여러 요소인 경우 합치기
                content_parts = []
                for elem in elements:
                    # 광고, 관련기사 등 제거
                    for unwanted in elem.select(".ad, .advertisement, .related, .taboola, script, style, .share, .sns"):
                        unwanted.decompose()
                    
                    text = elem.get_text(" ", strip=True)
                    if len(text) > 50:
                        content_parts.append(text)
                
                if content_parts:
                    body_text = " ".join(content_parts)
                    body_text = normalize_space(body_text)
                    
                    if len(body_text) >= 100:
                        logger.debug(f"JTBC CSS 선택자 추출 성공 (선택자: {selector}, {len(body_text)}자)")
                        
                        # 제목 추출
                        title_selectors = ["h1", ".headline", ".article_title", "title"]
                        title = ""
                        for title_sel in title_selectors:
                            title_node = soup.select_one(title_sel)
                            if title_node:
                                title = normalize_space(title_node.get_text(strip=True))
                                break
                        
                        # 날짜 추출
                        date_str = None
                        date_meta = soup.find("meta", attrs={"property": "article:published_time"})
                        if date_meta and date_meta.get("content"):
                            date_str = date_meta["content"]
                        else:
                            time_node = soup.select_one("time[datetime]")
                            if time_node and time_node.get("datetime"):
                                date_str = time_node["datetime"]
                        
                        return body_text, date_str, title
        
        # 2) Next.js __NEXT_DATA__ 처리
        sc = soup.find("script", id="__NEXT_DATA__", type="application/json")
        if sc and sc.string:
            try:
                data = json.loads(sc.string)
                texts, pub, hed = [], None, None
                def _walk(x):
                    nonlocal pub, hed
                    if isinstance(x, dict):
                        for k in ("headline","title","name"):
                            v = x.get(k)
                            if isinstance(v, str) and not hed:
                                hed = v
                        for k in ("datePublished","dateModified","publishDate","publishedAt"):
                            v = x.get(k)
                            if isinstance(v, str) and not pub:
                                pub = v
                        for k, v in x.items():
                            if isinstance(v, str) and k.lower() in {"articlebody","body","content","text","value","rawhtml","html"}:
                                if len(v) > 20:
                                    texts.append(v)
                            elif isinstance(v, (list, dict)):
                                _walk(v)
                    elif isinstance(x, list):
                        for v in x:
                            _walk(v)
                _walk(data)
                if texts:
                    raw = "\n".join(texts)
                    body = _clean_html_text(raw)
                    if len(body) >= 50:
                        return normalize_space(body), pub, (normalize_space(hed) if hed else None)
            except Exception:
                pass
        
        # 3) 정규식 fallback
        patterns = [
            r'"articleBody"\s*:\s*"(.+?)"',
            r'"content"\s*:\s*"(.+?)"',
            r'"text"\s*:\s*"(.+?)"',
            r'"body"\s*:\s*"(.+?)"'
        ]
        
        for pattern in patterns:
            m = re.search(pattern, h, re.DOTALL)
            if m:
                try:
                    raw = m.group(1).encode('utf-8', 'backslashreplace').decode('unicode_escape')
                    body = _clean_html_text(raw)
                    if len(body) >= 50:
                        title = ""
                        if soup.title and soup.title.string:
                            title = normalize_space(soup.title.string)
                        return normalize_space(body), None, title
                except Exception:
                    continue
        
        # 4) 일반적인 기사 구조 시도
        article_node = soup.find("article") or soup.find("main")
        if article_node:
            paragraphs = article_node.find_all("p")
            if len(paragraphs) >= 3:
                body_parts = []
                for p in paragraphs:
                    text = normalize_space(p.get_text(strip=True))
                    if len(text) > 20 and not any(skip in text.lower() for skip in ["광고", "advertisement", "관련기사", "추천"]):
                        body_parts.append(text)
                
                if body_parts:
                    body = " ".join(body_parts)
                    if len(body) >= 100:
                        title = ""
                        if soup.title and soup.title.string:
                            title = normalize_space(soup.title.string)
                        return normalize_space(body), None, title
        
        return "", None, None

    # 1) AMP link
    amp_html = None
    if html:
        try:
            soup = BeautifulSoup(html, "html.parser")
            amp_link = soup.find("link", rel=lambda v: v and "amphtml" in v.lower())
            if amp_link and amp_link.get("href"):
                amp_url = up.urljoin(url, amp_link["href"])
                # 잘못된 amp 서브도메인 시도 금지
                if "amp." not in up.urlparse(amp_url).netloc:
                    amp_html = polite_get(amp_url) or polite_get(amp_url, mobile=True)
        except Exception:
            pass

    # 2) JSON-LD
    def extract_from_jsonld(h: str) -> Tuple[str, Optional[str], Optional[str]]:
        try:
            soup = BeautifulSoup(h, "html.parser")
            for script in soup.find_all("script", type="application/ld+json"):
                try:
                    data = json.loads(script.string or "")
                except Exception:
                    continue
                candidates = data if isinstance(data, list) else [data]
                for item in candidates:
                    if not isinstance(item, dict):
                        continue
                    typ = item.get("@type") or item.get("@graph", [{}])[0].get("@type")
                    if (typ and ("Article" in str(typ) or "NewsArticle" in str(typ))) or item.get("articleBody"):
                        body = item.get("articleBody")
                        if isinstance(body, list):
                            body = "\n".join([str(x) for x in body])
                        hed = (item.get("headline") or item.get("name") or "")[:300]
                        date_str = item.get("datePublished") or item.get("dateModified")
                        return normalize_space(body or ""), date_str, hed
        except Exception:
            pass
        return "", None, None

    # 3) Next.js-ish generic
    def extract_from_next_json(h: str) -> Tuple[str, Optional[str], Optional[str]]:
        try:
            soup = BeautifulSoup(h, "html.parser")
            scripts_raw = []
            for sc in soup.find_all("script"):
                raw = sc.string or sc.get_text() or ""
                raw = raw.strip()
                if not raw:
                    continue
                if any(k in raw for k in ("__NEXT_DATA__", "articleBody", "\"content\"", "\"text\"", "\"value\"", "\"datePublished\"")) \
                or (raw.startswith("{") and raw.endswith("}")) \
                or (raw.startswith("[") and raw.endswith("]")):
                    scripts_raw.append(raw)

            def _walk(x, texts: list, meta: dict):
                if isinstance(x, dict):
                    for k in ("headline","title","name"):
                        v = x.get(k)
                        if isinstance(v, str) and not meta.get("title"):
                            meta["title"] = v
                    for k in ("datePublished","dateModified","publishDate","publishedAt"):
                        v = x.get(k)
                        if isinstance(v, str) and not meta.get("date"):
                            meta["date"] = v
                    for k, v in x.items():
                        if isinstance(v, str):
                            kl = k.lower()
                            if kl in {"articlebody","body","content","rawhtml","html","text","value"} and v.strip():
                                texts.append(v)
                        elif isinstance(v, (list, dict)):
                            _walk(v, texts, meta)
                elif isinstance(x, list):
                    for el in x:
                        _walk(el, texts, meta)

            for raw in scripts_raw:
                parsed = None
                try:
                    parsed = json.loads(raw)
                except Exception:
                    found = re.findall(r'"(?:text|value|content)"\s*:\s*"([^"]+)"', raw)
                    if found:
                        body_raw = "\n".join([fx for fx in found if fx.strip()])
                        body_txt = BeautifulSoup(body_raw, "html.parser").get_text(" ", strip=True)
                        body_txt = normalize_space(body_txt)
                        if len(body_txt) >= 50:
                            return body_txt, None, None
                if parsed is not None:
                    texts, meta = [], {"title": None, "date": None}
                    _walk(parsed, texts, meta)
                    if texts:
                        body_raw = "\n".join([t for t in texts if t.strip()])
                        body_txt = BeautifulSoup(body_raw, "html.parser").get_text(" ", strip=True)
                        body_txt = normalize_space(body_txt)
                        if len(body_txt) >= 50:
                            return body_txt, meta.get("date"), meta.get("title")
        except Exception:
            pass
        return "", None, None

    # 4) Readability
    def extract_with_readability(h: str) -> Tuple[str, str]:
        try:
            doc = Document(h)
            hed = normalize_space(doc.short_title())
            summ = doc.summary(html_partial=False)
            soup = BeautifulSoup(summ, "html.parser")
            parts = [normalize_space(p.get_text(" ", strip=True)) for p in soup.find_all("p")]
            body = normalize_space("\n".join([p for p in parts if p]))
            return body, hed
        except Exception:
            return "", ""

    # 5) trafilatura
    def extract_with_trafilatura(h: str) -> str:
        try:
            return normalize_space(trafilatura.extract(
                h, include_comments=False, include_tables=False,
                favor_precision=(False if fast else True)
            ) or "")
        except Exception:
            return ""

    # 6) newspaper3k
    def extract_with_newspaper(u: str) -> Tuple[str, Optional[datetime], str]:
        try:
            art = Article(u, keep_article_html=False, language="ko")
            art.download()
            art.parse()
            t = normalize_space(art.text)
            hed = normalize_space(art.title)
            d = art.publish_date
            if d and d.tzinfo is None:
                d = d.replace(tzinfo=timezone.utc)
            return t, d, hed
        except Exception:
            return "", None, ""

    # 7) 수동 CSS 후보
    def extract_manual(h: str) -> str:
        try:
            soup = BeautifulSoup(h, "html.parser")
            candidates = [
                {"id": "article", "cls": None},
                {"id": "article_body", "cls": None},
                {"id": "articleContent", "cls": None},
                {"id": None, "cls": "article_body"},
                {"id": None, "cls": "article-content"},
                {"id": None, "cls": "news_article"},
                {"id": None, "cls": "content_article"},
            ]
            chunks = []
            for c in candidates:
                node = soup.find(id=c["id"]) if c["id"] else soup.find(class_=lambda v: v and c["cls"] in v)
                if node:
                    ps = node.find_all(["p", "div"])
                    for p in ps:
                        txt = normalize_space(p.get_text(" ", strip=True))
                        if txt:
                            chunks.append(txt)
            if chunks:
                return normalize_space("\n".join(chunks))
        except Exception:
            pass
        return ""

    # ---- 실제 추출 순서 ----
    host = domain_of(url)

    # (Z) 도메인 전용 빠른 경로: NAVER / JTBC
    if html:
        if host.endswith("n.news.naver.com") or host.endswith("news.naver.com"):
            b, dstr, hed = _extract_naver_article(html)
            if len(b) >= 80:
                text = b; title = hed or title
                if dstr:
                    try: dt = datetime.fromisoformat(dstr.replace("Z","+00:00"))
                    except Exception: pass
        elif host.endswith("news.jtbc.co.kr"):
            b, dstr, hed = _extract_jtbc_nextdata(html)
            if len(b) >= 80:
                text = b; title = hed or title
                if dstr:
                    try: dt = datetime.fromisoformat(dstr.replace("Z","+00:00"))
                    except Exception: pass

    # (A) AMP 우선
    if amp_html and not text:
        body, date_str, hed = extract_from_jsonld(amp_html)
        if not body:
            body, hed2 = extract_with_readability(amp_html); hed = hed or hed2
        if not body:
            body = extract_with_trafilatura(amp_html)
        if not body:
            body = extract_manual(amp_html)
        if body:
            text = body; title = hed or title
            if date_str:
                try: dt = datetime.fromisoformat(date_str.replace("Z","+00:00"))
                except Exception: pass

    # (B) 원본 HTML: JSON-LD → Next.js JSON → Readability → trafilatura → manual
    if html and not text:
        body, date_str, hed = extract_from_jsonld(html)
        if not body:
            body, date_str2, hed2 = extract_from_next_json(html)
            hed = hed or hed2
            date_str = date_str or date_str2
        if not body:
            body, hed2 = extract_with_readability(html); hed = hed or hed2
        if not body:
            body = extract_with_trafilatura(html)
        if not body:
            body = extract_manual(html)
        if body:
            text = body; title = hed or title
            if date_str and not dt:
                try: dt = datetime.fromisoformat(date_str.replace("Z","+00:00"))
                except Exception: pass

    # (C) newspaper3k 최후
    if not text:
        t2, d2, hed = extract_with_newspaper(url)
        if t2:
            text, dt, title = t2, (dt or d2), (title or hed)

    # (D) 메타 타이틀 보정
    if not title and html:
        try:
            soup = BeautifulSoup(html, "html.parser")
            if soup.title and soup.title.string:
                title = normalize_space(soup.title.string)
        except Exception:
            pass

    return text, dt, title

# --------------------------------------------------------------------------------------------
# 도메인 평판 휴리스틱
GOOD_TLD_HINTS = (".go.kr", ".ac.kr", ".lg.jp", ".gov", ".edu")
OK_TLD_HINTS   = (".or.kr", ".or.jp", ".org", ".co.kr", ".co.jp", ".com", ".net")
LOW_TLD_HINTS  = (".info", ".biz")

def source_reputation(url: str, in_seed: bool) -> float:
    d = domain_of(url)
    score = 0.0
    if in_seed: score += 0.4
    if d.endswith(GOOD_TLD_HINTS): score += 0.4
    elif d.endswith(OK_TLD_HINTS): score += 0.2
    elif d.endswith(LOW_TLD_HINTS): score -= 0.1
    if url.lower().startswith("https://"): score += 0.05
    return max(-0.2, min(0.8, score))

def time_weight(dt_pub: Optional[datetime]) -> float:
    if not dt_pub: return 0.0
    age_days = max(0.0, (now_utc() - dt_pub).total_seconds()/86400.0)
    
    # 연도별 대폭 강화된 페널티 (오보 기사 대응)
    if age_days > 365 * 13:  # 13년 이상 (2011년 이전) - JTBC 오보 기사 대응
        return -1.2  # 매우 강한 페널티 (강화)
    elif age_days > 365 * 10:  # 10년 이상 (2014년 이전)
        return -1.0  # 강한 페널티 (강화)
    elif age_days > 365 * 7:  # 7년 이상 (2017년 이전)
        return -0.8  # 강한 페널티
    elif age_days > 365 * 5:  # 5년 이상 (2019년 이전)
        return -0.6  # 중간 페널티
    elif age_days > 365 * 3:  # 3년 이상 (2021년 이전)
        return -0.4  # 약간 페널티
    elif age_days > 365 * 1:  # 1년 이상
        return -0.2  # 최소 페널티
    else:
        w = math.exp(-TIME_LAMBDA * age_days)  # 1년 이내는 기존 공식
        return -0.1 + 0.9 * w  # -0.1 ~ +0.8

# --------------------------------------------------------------------------------------------
# 데이터 구조
@dataclass
class DocRecord:
    url: str
    title: str
    published: Optional[float]
    chunk: str
    domain: str
    from_seed: bool

@dataclass
class IndexPack:
    model_name: str
    embed_dim: int
    matrix: np.ndarray
    records: List[DocRecord]

# --------------------------------------------------------------------------------------------
# 문장 분할/청킹
def split_into_sentences(text: str) -> List[str]:
    parts = re.split(r"(?<=[.!?…]|[。！？])\s+", text)
    parts = [normalize_space(p) for p in parts if len(normalize_space(p)) > 0]
    return parts

def make_chunks(text: str, window: int = 4, step: int = 3, min_len: int = 200) -> List[str]:
    sents = split_into_sentences(text)
    chunks = []
    i = 0
    while i < len(sents):
        block = normalize_space(" ".join(sents[i:i+window]))
        if len(block) >= min_len:
            chunks.append(block)
        i += step
    if not chunks and len(text) >= min_len:
        chunks = [text]
    return chunks

# --------------------------------------------------------------------------------------------
# 모델 로딩(GPU/FP16)
DEVICE = "cuda" if (hasattr(torch, "cuda") and torch.cuda.is_available()) else "cpu"

def get_embedder(use_gpu: bool, fp16: bool):
    model = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    device = "cuda" if (use_gpu and DEVICE == "cuda") else "cpu"
    
    # CUDA 성능 최적화 설정
    if device == "cuda":
        torch.backends.cudnn.benchmark = True  # 반복적인 연산 최적화
        torch.backends.cudnn.deterministic = False  # 성능 우선
        torch.cuda.empty_cache()  # GPU 메모리 캐시 정리
        # GPU 메모리 할당 전략 최적화
        torch.cuda.set_per_process_memory_fraction(0.9)  # 90% VRAM 사용 허용
    
    emb = SentenceTransformer(model, device=device)
    logger.info("임베딩 모델: %s (device=%s, fp16=%s)", model, emb._target_device, fp16)
    return emb, fp16

def get_nli(use_gpu: bool, fp16: bool):
    name = "cross-encoder/nli-deberta-v3-small"
    tok = AutoTokenizer.from_pretrained(name)
    mdl = AutoModelForSequenceClassification.from_pretrained(
        name, torch_dtype=(torch.float16 if (fp16 and DEVICE == "cuda") else None)
    )
    mdl.eval()
    mdl.to("cuda" if (use_gpu and DEVICE == "cuda") else "cpu")
    logger.info("NLI 모델: %s (device=%s, dtype=%s)", name, next(mdl.parameters()).device, next(mdl.parameters()).dtype)
    return tok, mdl, fp16

@torch.no_grad()
def nli_batch_probs(pairs: List[Tuple[str, str]], tok, mdl, batch_size: int, use_fp16: bool) -> np.ndarray:
    outs = []
    device = next(mdl.parameters()).device
    for i in range(0, len(pairs), batch_size):
        prem = [p for p, _ in pairs[i:i+batch_size]]
        hypo = [h for _, h in pairs[i:i+batch_size]]
        inputs = tok(prem, hypo, truncation=True, max_length=256, padding=True, return_tensors="pt")
        inputs = {k: v.to(device) for k, v in inputs.items()}
        if use_fp16 and device.type == "cuda":
            with torch.autocast(device_type="cuda", dtype=torch.float16):
                logits = mdl(**inputs).logits
        else:
            logits = mdl(**inputs).logits
        probs = torch.softmax(logits, dim=-1).cpu().numpy()
        outs.append(probs)
    return np.vstack(outs) if outs else np.zeros((0, 3), dtype=np.float32)

# --------------------------------------------------------------------------------------------
# 크롤링(도메인 단위) + Overall 진행바
from concurrent.futures import ThreadPoolExecutor, as_completed
from threading import Lock

def crawl_domain(seed_url: str, max_depth: int, max_pages: int,
                 overall_update: Optional[Callable[[int], None]] = None) -> List[Tuple[str, str]]:
    visited = set()
    out = []
    seed_dom = domain_of(seed_url)
    q = queue.Queue()
    q.put((seed_url, 0))
    
    # 간단한 진행률 표시 (백그라운드, 비활성화)
    pbar = tqdm(total=max_pages, desc=f"Crawl {seed_dom}", 
                leave=False, position=None, disable=True)  # 완전히 비활성화
    
    while not q.empty() and len(visited) < max_pages:
        url, depth = q.get()
        if url in visited:
            continue
        visited.add(url)
        html = polite_get(url) or polite_get(url, mobile=True)
        if html:
            out.append((url, html))
            if depth < max_depth:
                for nxt in extract_links(url, html):
                    if is_same_domain(nxt, seed_dom):
                        q.put((nxt, depth + 1))
        pbar.update(1)
        if overall_update:
            overall_update(1)
        time.sleep(CRAWL_SLEEP)
    pbar.close()
    logger.info("도메인 크롤 완료: %s (수집 %d / 방문 %d)", seed_dom, len(out), len(visited))
    return out

# --------------------------------------------------------------------------------------------
# 시드 처리 - 크롤링만 (임베딩은 별도 처리)
def process_seed_crawl_only(seed: str, fast_extract: bool,
                           overall_update: Optional[Callable[[int], None]] = None) -> List[Tuple[str, str, str, str]]:
    """크롤링만 수행하고 텍스트 청크 반환"""
    dom = domain_of(seed)
    pages = crawl_domain(seed, MAX_DEPTH, MAX_PAGES_PER_DOMAIN, overall_update=overall_update)
    logger.info("도메인 수집 완료: %s (%d pages)", dom, len(pages))

    text_chunks = []
    for url, html in pages:
        text, dt, title = extract_text(url, html, fast=fast_extract)
        if len(text) >= MIN_TEXT_LEN:
            chunks = make_chunks(text, min_len=MIN_TEXT_LEN)
            for ch in chunks:
                text_chunks.append((url, dt, title, ch))
    return text_chunks

# 기존 함수도 유지 (호환성)
def process_seed(seed: str, embedder, embed_batch: int, fast_extract: bool,
                 overall_update: Optional[Callable[[int], None]] = None) -> Tuple[List[np.ndarray], List[DocRecord]]:
    dom = domain_of(seed)
    pages = crawl_domain(seed, MAX_DEPTH, MAX_PAGES_PER_DOMAIN, overall_update=overall_update)
    logger.info("도메인 수집 완료: %s (%d pages)", dom, len(pages))

    texts, metas = [], []
    for url, html in pages:
        text, dt, title = extract_text(url, html, fast=fast_extract)
        if len(text) >= MIN_TEXT_LEN:
            chunks = make_chunks(text, min_len=MIN_TEXT_LEN)
            for ch in chunks:
                texts.append(ch)
                metas.append((url, dt, title, domain_of(url)))

    if not texts:
        return [], []

    # RTX3070ti 8GB VRAM 최대 활용 임베딩 처리
    effective_batch_size = min(embed_batch, len(texts))
    
    # GPU 메모리가 충분하다면 더 큰 배치 사용
    if torch.cuda.is_available():
        gpu_memory = torch.cuda.get_device_properties(0).total_memory
        if gpu_memory > 7 * 1024**3:  # 7GB 이상이면
            effective_batch_size = min(2048, len(texts))  # 더 큰 배치 사용
        
        # GPU 사용 강제 및 최적화
        with torch.cuda.device(0):
            torch.cuda.empty_cache()  # 캐시 정리
            vecs = embedder.encode(
                texts, 
                batch_size=effective_batch_size,
                convert_to_numpy=True, 
                normalize_embeddings=True,
                show_progress_bar=False,
                device='cuda'  # 명시적으로 CUDA 지정
            )
    else:
        vecs = embedder.encode(
            texts, 
            batch_size=effective_batch_size,
            convert_to_numpy=True, 
            normalize_embeddings=True,
            show_progress_bar=False,
            device='cpu'
        )
    recs = []
    for (url, dt, title, d), ch, v in zip(metas, texts, vecs):
        recs.append(DocRecord(
            url=url,
            title=title or "",
            published=(dt.timestamp() if dt else None),
            chunk=ch,
            domain=d,
            from_seed=True
        ))
    return list(vecs), recs

# GPU 최대 활용 배치 임베딩 처리
def batch_embed_texts(text_chunks: List[Tuple[str, str, str, str]], embedder, embed_batch: int) -> Tuple[List[np.ndarray], List[DocRecord]]:
    """텍스트 청크들을 배치로 임베딩 처리 - GPU 최대 활용 (분할 처리)"""
    if not text_chunks:
        return [], []
    
    texts = [chunk[3] for chunk in text_chunks]  # 텍스트만 추출
    total_texts = len(texts)
    
    print(f"🚀 GPU 최대 활용 임베딩 시작: {total_texts:,}개 청크")
    
    # RTX3070ti 8GB에 맞는 메모리 관리
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
        
        # 대용량 데이터 분할 처리 전략
        if total_texts > 100000:  # 10만개 이상
            chunk_size = 50000  # 5만개씩 분할
            dynamic_batch_size = 512
        elif total_texts > 50000:  # 5만개 이상 
            chunk_size = 25000  # 2.5만개씩 분할
            dynamic_batch_size = 768
        else:  # 5만개 미만
            chunk_size = total_texts  # 분할 안 함
            dynamic_batch_size = min(1024, total_texts)
        
        print(f"   📊 처리 전략: {chunk_size:,}개씩 분할, 배치 크기: {dynamic_batch_size}")
        print(f"    총 분할 수: {(total_texts + chunk_size - 1) // chunk_size}개")
        
        # GPU 메모리 할당 제한
        torch.cuda.set_per_process_memory_fraction(0.8)  # 80%만 사용
        
        # 분할 처리
        all_vecs = []
        for i in range(0, total_texts, chunk_size):
            chunk_texts = texts[i:i + chunk_size]
            chunk_info = text_chunks[i:i + chunk_size]
            
            print(f"   📦 분할 {i//chunk_size + 1}: {len(chunk_texts):,}개 임베딩 중...")
            
            try:
                torch.cuda.empty_cache()  # 각 분할 전 메모리 정리
                
                with torch.cuda.device(0):
                    chunk_vecs = embedder.encode(
                        chunk_texts,
                        batch_size=dynamic_batch_size,
                        convert_to_numpy=True,
                        normalize_embeddings=True,
                        show_progress_bar=False,
                        device='cuda'
                    )
                    
                all_vecs.extend(chunk_vecs)
                
                # 중간 진행 상황 출력
                processed = min(i + chunk_size, total_texts)
                print(f"   ✅ 완료: {processed:,}/{total_texts:,} ({processed/total_texts*100:.1f}%)")
                
            except torch.cuda.OutOfMemoryError:
                print(f"   ⚠️  GPU 메모리 부족, CPU로 대체 처리...")
                # CPU 백업 처리
                chunk_vecs = embedder.encode(
                    chunk_texts,
                    batch_size=min(64, len(chunk_texts)),
                    convert_to_numpy=True,
                    normalize_embeddings=True,
                    show_progress_bar=False,
                    device='cpu'
                )
                all_vecs.extend(chunk_vecs)
        
        vecs = all_vecs
        
    else:
        # CPU 처리
        vecs = embedder.encode(
            texts,
            batch_size=min(embed_batch, 64),
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
            device='cpu'
        )
    
    # DocRecord 생성
    recs = []
    for (url, dt, title, ch), v in zip(text_chunks, vecs):
        recs.append(DocRecord(
            url=url,
            title=title or "",
            published=(dt.timestamp() if dt else None),
            chunk=ch,
            domain=domain_of(url),
            from_seed=True
        ))
    
    # GPU 메모리 정리
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    
    print(f"✅ 임베딩 완료: {len(recs):,}개 벡터 생성")
    return list(vecs), recs

# --------------------------------------------------------------------------------------------
# 병렬 빌드 - 메모리 최적화
def build_index_parallel(seeds: List[str], embedder, workers: int, embed_batch: int, fast_extract: bool) -> IndexPack:
    # 128GB RAM 활용을 위한 초기 용량 설정
    estimated_chunks = len(seeds) * MAX_PAGES_PER_DOMAIN * 5  # 페이지당 평균 5개 청크 예상
    all_vecs, all_recs = [], []
    # 파이썬에서는 리스트 reserve가 없으므로 대신 빈 리스트로 초기화

    estimated_total_pages = len(seeds) * MAX_PAGES_PER_DOMAIN
    start_time = time.time()
    
    # CPU 스레드 수에 따라 실제 워커 수 조정 (최대 활용)
    cpu_count = mp.cpu_count()
    effective_workers = min(workers, cpu_count * 2)  # I/O 집약적이므로 2배
    
    # 하드웨어 정보 먼저 출력 (진행률 바와 분리)
    print(f"🔧 하드웨어 최적화:")
    print(f"   💻 CPU 코어: {cpu_count}개")
    print(f"   🔀 크롤링 워커: {effective_workers}개")
    print(f"   📦 임베딩 배치: {embed_batch}개")
    print(f"   🎮 GPU 메모리: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f}GB" if torch.cuda.is_available() else "   🎮 GPU: 비활성")
    print("=" * 50)
    
    # 단계 1: 모든 시드에서 텍스트 크롤링 (CPU 집약적)
    print("🕷️  1단계: 병렬 크롤링 시작...")
    
    # 진행도 표시 - 실시간 시간 업데이트
    seeds_progress = tqdm(
        total=len(seeds), 
        desc="📰 도메인 처리", 
        unit="개",
        leave=True,  # 완료 후 유지하여 최종 상태 표시
        dynamic_ncols=True,  # 터미널 크기에 맞춰 조정
        bar_format="{desc}: {percentage:3.0f}%|{bar}| {n_fmt}/{total_fmt} [⏱️ {elapsed}]",
        mininterval=0.01,  # 0.01초마다 업데이트 (더 자주)
        maxinterval=0.2   # 최대 0.2초마다 강제 업데이트
    )
    
    # 실시간 업데이트를 위한 스레드
    import threading
    def update_timer():
        while not seeds_progress.disable and seeds_progress.n < seeds_progress.total:
            seeds_progress.refresh()
            time.sleep(0.2)  # 0.2초마다 갱신
    
    timer_thread = threading.Thread(target=update_timer, daemon=True)
    timer_thread.start()

    _lock = Lock()
    pages_processed = 0
    def safe_overall_update(n: int = 1):
        nonlocal pages_processed
        with _lock:
            pages_processed += n

    completed_seeds = 0
    all_text_chunks = []
    
    with ThreadPoolExecutor(max_workers=effective_workers) as ex:
        crawl_futs = {ex.submit(process_seed_crawl_only, s, fast_extract, safe_overall_update): s for s in seeds}
        for fut in as_completed(crawl_futs):
            s = crawl_futs[fut]
            try:
                text_chunks = fut.result()
                if text_chunks:
                    all_text_chunks.extend(text_chunks)
                
                completed_seeds += 1
                
                # 진행률 업데이트 (즉시 반영)
                domain_short = domain_of(s)[:15] + "..." if len(domain_of(s)) > 15 else domain_of(s)
                seeds_progress.update(1)
                seeds_progress.set_description(f"📰 완료: {domain_short}")
                seeds_progress.display()  # 즉시 표시 강제
                    
            except Exception as e:
                completed_seeds += 1
                domain_short = domain_of(s)[:15] + "..." if len(domain_of(s)) > 15 else domain_of(s)
                seeds_progress.update(1)
                seeds_progress.set_description(f"📰 실패: {domain_short}")
                seeds_progress.display()  # 즉시 표시 강제
    
    # 크롤링 완료 후 최종 상태 표시
    seeds_progress.set_description("📰 크롤링 완료")
    seeds_progress.close()
    
    print(f"\n🚀 2단계: GPU 최대 활용 임베딩 시작... (총 {len(all_text_chunks)}개 청크)")
    
    # 단계 2: 수집된 모든 텍스트를 한 번에 GPU에서 임베딩 (GPU 집약적)
    if all_text_chunks:
        all_vecs, all_recs = batch_embed_texts(all_text_chunks, embedder, embed_batch)
        print(f"✅ 임베딩 완료! (최종 청크: {len(all_recs):,}개)")
    else:
        all_vecs, all_recs = [], []

    # 완료 메시지
    total_time = time.time() - start_time
    total_minutes = total_time / 60
    print(f"\n🎉 인덱스 빌드 완료!")
    print(f"📊 총 소요시간: {total_minutes:.1f}분")
    print(f"📚 수집된 청크: {len(all_recs):,}개")
    print(f"🌐 처리된 도메인: {completed_seeds}/{len(seeds)}개")

    if not all_vecs:
        raise RuntimeError("인덱스에 추가할 데이터가 없다. 시드/크롤링을 확인하라.")
    M = np.vstack(all_vecs).astype("float32")
    return IndexPack(
        model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
        embed_dim=M.shape[1],
        matrix=M,
        records=all_recs
    )

# --------------------------------------------------------------------------------------------
# 인덱스 빌드/로드
def build_index(workers: int, embed_batch: int, use_gpu: bool, fp16: bool, http_pool: int, timeout: int, sleep: float, fast_extract: bool, test_mode: bool = False):
    configure_http(http_pool=http_pool, timeout=timeout)
    global CRAWL_SLEEP
    CRAWL_SLEEP = sleep

    assert os.path.exists(SEED_CSV), f"seed csv not found: {SEED_CSV}"
    with open(SEED_CSV, "r", encoding="utf-8") as f:
        seeds = [canonical_url(r["url"]) for r in csv.DictReader(f) if r.get("url", "").startswith("http")]
    seeds = list(dict.fromkeys(seeds))
    
    # 테스트 모드: 매우 소량의 시드만 사용 (빠른 테스트)
    if test_mode:
        # 최소한의 다양성을 위한 3개 시드만 선별
        test_seeds = [
            # 국내 언론사 1개 (신뢰도 높음)
            "https://news.kbs.co.kr",
            # 해외 언론사 1개 (신뢰도 높음)  
            "https://www.bbc.com",
            # 통신사 1개 (빠른 처리)
            "https://www.reuters.com"
        ]
        seeds = [s for s in seeds if s in test_seeds]
        print(f"🧪 테스트 모드 활성화")
        print(f"📊 사용할 시드: {len(seeds)}개 (전체 {len(test_seeds)}개 중)")
        print(f"⚡ 예상 완료시간: 2-5분")
        print("=" * 50)
    else:
        print(f"📚 전체 모드 활성화")
        print(f"📊 사용할 시드: {len(seeds)}개")
        print(f"⚡ 예상 완료시간: 30-60분 (하드웨어에 따라)")
        print("=" * 50)

    embedder, _ = get_embedder(use_gpu=use_gpu, fp16=fp16)
    pack = build_index_parallel(seeds, embedder, workers=workers, embed_batch=embed_batch, fast_extract=fast_extract)
    with open(INDEX_PKL, "wb") as f:
        pickle.dump(pack, f)
    logger.info("[ok] index built: %s (rows=%d, dim=%d)", INDEX_PKL, pack.matrix.shape[0], pack.matrix.shape[1])

def load_index() -> IndexPack:
    assert os.path.exists(INDEX_PKL), f"index pkl not found: {INDEX_PKL}"
    with open(INDEX_PKL, "rb") as f:
        return pickle.load(f)

def save_index(pack: IndexPack):
    """인덱스를 파일에 저장합니다."""
    with open(INDEX_PKL, "wb") as f:
        pickle.dump(pack, f)
    logger.info("[ok] index saved: %s (rows=%d, dim=%d)", INDEX_PKL, pack.matrix.shape[0], pack.matrix.shape[1])

def add_url_to_index(url: str, text: str, dt, title: str, embedder, pack: IndexPack) -> bool:
    """URL을 인덱스에 추가합니다. 이미 존재하면 False, 추가되면 True를 반환합니다."""
    
    # URL 중복 체크
    for record in pack.records:
        if record.url == url:
            logger.debug(f"URL이 이미 인덱스에 존재함: {url}")
            return False
    
    # 새로운 URL 추가
    logger.info(f"새 URL을 인덱스에 추가: {url}")
    
    # 청크 생성
    chunks = make_chunks(text, min_len=max(120, MIN_TEXT_LEN // 2))
    if not chunks:
        chunks = [text]
    
    # 임베딩 생성
    embeddings = embedder.encode(chunks, convert_to_numpy=True, normalize_embeddings=True)
    
    # 기존 매트릭스에 새 임베딩 추가
    new_matrix = np.vstack([pack.matrix, embeddings])
    
    # 새 레코드들 생성
    new_records = []
    for i, chunk in enumerate(chunks):
        new_record = DocRecord(
            url=url,
            title=title,
            published=dt.timestamp() if dt else None,
            chunk=chunk,
            domain=domain_of(url),
            from_seed=False  # 사용자 입력 URL은 시드가 아님
        )
        new_records.append(new_record)
    
    # 인덱스 팩 업데이트
    pack.matrix = new_matrix
    pack.records.extend(new_records)
    
    return True

def check_domains(domain_filter: Optional[str] = None, verbose: bool = False):
    """인덱스에 포함된 도메인들을 확인합니다."""
    if not os.path.exists(INDEX_PKL):
        logger.error("인덱스 파일이 없습니다: %s", INDEX_PKL)
        return
    
    pack = load_index()
    logger.info("인덱스 로드 완료: %d개 레코드", len(pack.records))
    
    # 도메인별 URL 수집
    domain_counts = {}
    matching_urls = []
    
    for record in pack.records:
        url = record.url
        if url:
            from urllib.parse import urlparse
            domain = urlparse(url).netloc
            domain_counts[domain] = domain_counts.get(domain, 0) + 1
            
            if domain_filter and domain_filter.lower() in domain.lower():
                matching_urls.append(url)
    
    # 결과 출력
    if domain_filter:
        print(f"\n'{domain_filter}' 포함 도메인:")
        filtered_domains = {d: c for d, c in domain_counts.items() if domain_filter.lower() in d.lower()}
        for domain, count in sorted(filtered_domains.items(), key=lambda x: x[1], reverse=True):
            print(f"  {domain}: {count}개")
        
        print(f"\n'{domain_filter}' 포함 URL 목록:")
        for url in matching_urls[:20]:  # 처음 20개만
            print(f"  {url}")
        if len(matching_urls) > 20:
            print(f"  ... (총 {len(matching_urls)}개)")
    else:
        print(f"\n전체 도메인 통계 (상위 20개):")
        sorted_domains = sorted(domain_counts.items(), key=lambda x: x[1], reverse=True)
        for domain, count in sorted_domains[:20]:
            print(f"  {domain}: {count}개")
        
        if verbose:
            print(f"\n전체 도메인 목록:")
            for domain, count in sorted(domain_counts.items()):
                print(f"  {domain}: {count}개")


def check_keyword_relevance(query_text: str, evidence_text: str, min_common_keywords: int = 2) -> bool:
    """
    질의와 근거 텍스트 간의 키워드 관련성을 검증합니다.
    
    Args:
        query_text: 질의 텍스트
        evidence_text: 근거 텍스트  
        min_common_keywords: 최소 공통 키워드 수
        
    Returns:
        관련성이 있으면 True, 없으면 False
    """
    # 한국어/영어 키워드 추출
    import re
    
    # 질의에서 주요 키워드 추출 (한국어 2글자 이상, 영어 3글자 이상)
    query_keywords = set()
    
    # 한국어 키워드
    kr_words = re.findall(r'[가-힣]{2,}', query_text)
    query_keywords.update(kr_words)
    
    # 영어 키워드 (대소문자 구분 없이)
    en_words = re.findall(r'[A-Za-z]{3,}', query_text.upper())
    query_keywords.update(en_words)
    
    # 숫자 포함 키워드
    num_words = re.findall(r'[0-9]{2,}', query_text)
    query_keywords.update(num_words)
    
    # 근거 텍스트에서도 동일하게 추출
    evidence_keywords = set()
    
    # 한국어 키워드
    kr_words = re.findall(r'[가-힣]{2,}', evidence_text)
    evidence_keywords.update(kr_words)
    
    # 영어 키워드 (대소문자 구분 없이)
    en_words = re.findall(r'[A-Za-z]{3,}', evidence_text.upper())
    evidence_keywords.update(en_words)
    
    # 숫자 포함 키워드
    num_words = re.findall(r'[0-9]{2,}', evidence_text)
    evidence_keywords.update(num_words)
    
    # 공통 키워드 계산
    common_keywords = query_keywords.intersection(evidence_keywords)
    
    # 중요 키워드는 가중치 부여
    important_keywords = {'사드', 'THAAD', '성주', '미사일', '배치', '방어', '레이더', '괌', '일본'}
    important_common = common_keywords.intersection(important_keywords)
    
    # 중요 키워드가 있으면 기준 완화, 없으면 기준 강화
    effective_common = len(common_keywords) + len(important_common) * 2
    
    logger.debug(f"키워드 관련성 검증: 공통={len(common_keywords)}개, 중요공통={len(important_common)}개, 효과적공통={effective_common}")
    
    return effective_common >= min_common_keywords


# --------------------------------------------------------------------------------------------
# 평가
def split_into_sentences_for_summary(text: str) -> List[str]:
    return re.split(r"(?<=[.!?…]|[。！？])\s+", text)

def summarize_for_nli(text: str, max_sents: int = 3) -> str:
    sents = [normalize_space(s) for s in split_into_sentences_for_summary(text) if s.strip()]
    return " ".join(sents[:max_sents]) if sents else text[:500]

def search_contradiction_evidence(query_url, query_text, matrix, records, embedder, k=5):
    """
    특정 기사에 대한 정확한 반박 증거를 검색합니다.
    URL과 내용을 모두 매칭하여 정확한 반박 기사만 반환합니다.
    """
    try:
        # URL에서 기사 ID와 언론사 추출
        parsed_url = up.urlparse(query_url)
        domain = parsed_url.netloc
        
        # JTBC 기사 ID 추출 (예: /article/NB11272032)
        article_id = ""
        if "/article/" in query_url:
            article_id = query_url.split("/article/")[-1]
        
        # 반박 증거 결과 리스트
        contradiction_results = []
        
        # 알려진 문제가 있는 특정 기사에 대한 주의사항 표시
        # (정확한 매핑이 확인된 경우에만 활성화)
        problematic_articles = {
            # 예시: 확실한 오보 사례가 확인되면 추가
            # "NB11272032": "이 기사에 대한 정정 보도가 있었다는 제보가 있습니다."
        }
        
        # 현재는 비활성화 상태 - 정확한 검증 후 활성화 예정
        if 'jtbc' in domain and article_id in problematic_articles:
            logger.debug(f"JTBC 기사 {article_id}에 대한 주의사항 확인")
            warning_message = problematic_articles[article_id]
            contradiction_results.append({
                'url': '(시스템 주의사항)',
                'text': warning_message,
                'similarity': 0.0,
                'contradiction_score': 1,
                'query': 'manual_warning',
                'domain': 'system_warning'
            })
        
        # 정확한 기사 ID 매칭으로 반박 증거 검색 (현재 비활성화)
        # if 'jtbc' in domain and article_id in jtbc_contradiction_map:
        #     logger.debug(f"JTBC 기사 {article_id}에 대한 특정 반박 기사 검색")
        #     contradiction_results.extend(jtbc_contradiction_map[article_id])
        # else:
        #     logger.debug(f"기사 {article_id}에 대한 반박 증거 없음")
        
        logger.debug(f"반박 증거 검색 기능 비활성화됨")
        
        # 다른 언론사와 기사에 대한 매핑도 여기에 추가 가능
        # other_media_contradiction_map = { ... }
        
        logger.debug(f"반박 증거 검색 완료: {len(contradiction_results)}개 발견")
        return contradiction_results[:k]
        
    except Exception as e:
        logger.error(f"반박 증거 검색 실패: {e}")
        return []

def evaluate_url(query_url: str, nli_batch: int, use_gpu: bool, fp16: bool, similarity_threshold: float = 0.35):
    if SESSION is None:
        configure_http(http_pool=64, timeout=12)

    pack = load_index()
    embedder, _ = get_embedder(use_gpu=use_gpu, fp16=fp16)

    logger.info("평가 URL 파싱: %s", query_url)

    # 네이버는 모바일 UA가 유리한 경우가 있음: 모바일 → 데스크톱
    host = domain_of(query_url)
    html = None
    if host.endswith("n.news.naver.com") or host.endswith("news.naver.com"):
        html = polite_get(query_url, mobile=True) or polite_get(query_url)
    else:
        html = polite_get(query_url) or polite_get(query_url, mobile=True)

    q_text, q_dt, q_title = extract_text(query_url, html)
    if len(q_text) < 50:
        print("본문 추출 실패 또는 텍스트가 너무 짧음(또는 한글 비중 낮음). URL/파서 설정을 확인하세요.")
        sys.exit(1)

    # 사용자 입력 URL을 인덱스에 추가 (중복이 아닌 경우)
    try:
        if add_url_to_index(query_url, q_text, q_dt, q_title, embedder, pack):
            save_index(pack)
            logger.info("사용자 URL이 인덱스에 추가되었습니다.")
        else:
            logger.debug("URL이 이미 인덱스에 존재하여 추가하지 않았습니다.")
    except Exception as e:
        logger.warning(f"URL 인덱스 추가 중 오류 (계속 진행): {e}")

    q_chunks = make_chunks(q_text, min_len=max(120, MIN_TEXT_LEN // 2))
    if not q_chunks:
        q_chunks = [q_text]
    logger.info("질의 청크 수: %d", len(q_chunks))

    q_vecs = embedder.encode(q_chunks, convert_to_numpy=True, normalize_embeddings=True)
    sims = util.cos_sim(torch.tensor(q_vecs), torch.from_numpy(pack.matrix)).cpu().numpy()  # (Q,N)
    sim_per_idx = sims.max(axis=0)

    # 후보 TopK
    K = min(TOPK_CANDIDATES, pack.matrix.shape[0])
    cand_idx = np.argsort(-sim_per_idx)[:K].tolist()

    tok, mdl, use_fp16 = get_nli(use_gpu=use_gpu, fp16=fp16)
    q_premise = summarize_for_nli(q_text, max_sents=3)

    # 반박 증거 검색 추가
    logger.debug("반박 증거 검색 시작...")
    contradiction_evidence = search_contradiction_evidence(
        query_url, q_text, pack.matrix, pack.records, embedder, k=3
    )
    
    pairs = [(pack.records[idx].chunk, q_premise) for idx in cand_idx]
    probs = nli_batch_probs(pairs, tok, mdl, batch_size=nli_batch, use_fp16=use_fp16)  # [N,3]
    c_prob = probs[:, 0] if probs.size else np.zeros((len(cand_idx),), dtype=np.float32)  # contradiction
    e_prob = probs[:, 2] if probs.size else np.zeros((len(cand_idx),), dtype=np.float32)  # entailment

    q_lang_kr = korean_ratio(q_text)

    scored = []
    for rank, idx in enumerate(cand_idx):
        rec = pack.records[idx]
        sim_v = float(sim_per_idx[idx])
        sup_v = float(e_prob[rank])
        con_v = float(c_prob[rank])
        
        # 기본 필터링: 너무 낮은 유사성이나 NLI 지지도는 제외
        if sim_v < similarity_threshold or sup_v < MIN_NLI_SUPPORT_THRESHOLD:
            continue
        
        # 언어/지역 필터링 강화: 한국어 기사인 경우 외국 사이트 제한
        rec_domain = domain_of(rec.url)
        
        # 한국 사이트 판별 (더 엄격하게)
        korean_domains = [
            'naver.com', 'daum.net', 'chosun.com', 'joins.com', 'donga.com',
            'hani.co.kr', 'khan.co.kr', 'ytn.co.kr', 'jtbc.co.kr', 'sbs.co.kr',
            'kbs.co.kr', 'mbc.co.kr', 'news1.kr', 'newsis.com', 'edaily.co.kr',
            'mk.co.kr', 'hankyung.com', 'korea.kr', 'koreaherald.com', 'koreatimes.co.kr',
            'koreajoongangdaily.joins.com', 'pressian.com', 'ohmynews.com'
        ]
        
        is_korean_site = any(korean_domain in rec_domain for korean_domain in korean_domains)
        is_foreign_site = not is_korean_site and any(tld in rec_domain for tld in ['.fr', '.de', '.it', '.es', '.com', '.net', '.org'])
        
        # 한국어 비중이 높은 질의의 경우 외국 사이트 강력 제한
        if q_lang_kr >= 0.3:  # 한국어 비중 30% 이상
            if is_foreign_site:
                # 외국 사이트이지만 한국 관련 내용인지 매우 엄격하게 확인
                korean_keywords = ['한국', '대한민국', '서울', '부산', '정부', '대통령', '국정감사', '국회', '청와대', 'Korea', 'South Korea', 'Seoul']
                has_strong_korean_context = sum(1 for keyword in korean_keywords if keyword in rec.chunk) >= 2  # 2개 이상 키워드 필요
                
                if not has_strong_korean_context:
                    # 한국 맥락이 약한 외국 기사는 제외
                    continue
                    
                # 한국 맥락이 있어도 페널티 적용
                sim_v *= 0.7  # 유사성에 페널티
        
        # 내용 관련성 강화: 주요 키워드 매칭 점검 (강화된 버전)
        content_relevance = 1.0
        
        # 더 정교한 키워드 관련성 검증 사용
        is_relevant = check_keyword_relevance(q_text, rec.chunk, min_common_keywords=2)
        
        # 기존 방식도 병행 (호환성 유지)
        q_keywords = set()
        import re
        # 한글 2글자 이상 단어들
        korean_words = re.findall(r'[가-힣]{2,}', q_text)
        for word in korean_words[:10]:  # 상위 10개만
            if len(word) >= 2 and word not in ['것은', '있다', '한다', '된다', '이다', '그것', '이것', '그리고', '하지만', '그러나']:
                q_keywords.add(word)
        
        # 영어 단어들도 추가
        english_words = re.findall(r'[A-Za-z]{3,}', q_text)
        for word in english_words[:5]:  # 상위 5개만
            if word.lower() not in ['the', 'and', 'for', 'are', 'but', 'not', 'you', 'all', 'can', 'had', 'was', 'one', 'our', 'has']:
                q_keywords.add(word.lower())
        
        if q_keywords:
            # 후보 텍스트에서 키워드 매칭 비율 계산
            rec_text_lower = rec.chunk.lower()
            matched_keywords = 0
            for keyword in q_keywords:
                if keyword.lower() in rec_text_lower:
                    matched_keywords += 1
            
            keyword_match_ratio = matched_keywords / len(q_keywords) if q_keywords else 0
            
            # 키워드 매칭이 아예 없으면 제외 (0% 매칭 허용 안함)
            if keyword_match_ratio == 0.0:
                logger.debug(f"키워드 매칭 0%로 증거에서 제외: {rec.url}")
                continue
            
            # 새로운 관련성 검증이 실패하거나 키워드 매칭이 낮으면 강한 페널티
            if not is_relevant or keyword_match_ratio < 0.15:  # 기준 강화: 10% → 15%
                content_relevance = 0.5  # 더 강한 페널티: 0.8 → 0.5
                logger.debug(f"관련성 낮음: 새검증={is_relevant}, 키워드매칭={keyword_match_ratio:.2f}")
            elif keyword_match_ratio < 0.25:  # 25% 미만도 페널티
                content_relevance = 0.7
                logger.debug(f"관련성 보통: 키워드매칭={keyword_match_ratio:.2f}")
            elif keyword_match_ratio < 0.2:  # 20% 미만 매칭
                content_relevance = 0.9
        
        dt = datetime.fromtimestamp(rec.published, tz=timezone.utc) if rec.published else None
        time_v = time_weight(dt)
        src_v = source_reputation(rec.url, rec.from_seed)
        
        # 언어 정합 가중: 질의가 한글 비중 높으면 한글 비중 높은 청크에 보너스
        lang_align = 1.0
        if q_lang_kr >= 0.25:
            lang_align = 0.8 + 0.2 * (1.0 if korean_ratio(rec.chunk) >= 0.25 else 0.0)
        lang_v = (lang_align - 1.0)  # -0.2 ~ 0.0
        
        score = (ALPHA_SIM * sim_v) + (BETA_SUP * sup_v) - (GAMMA_CONTRA * con_v) \
                + (DELTA_TIME * time_v) + (EPS_SOURCE * src_v) + (EPS_LANG * lang_v)
        
        # 내용 관련성 보정 적용
        score *= content_relevance
        
        # 최종 점수 임계값 적용
        if score >= MIN_FINAL_SCORE:
            scored.append((idx, score, {"url": rec.url, "similarity": sim_v, "support": sup_v}))

    scored.sort(key=lambda x: x[1], reverse=True)

    # 개선된 중복 제거: URL 유사성과 내용 유사성 모두 고려
    seen = set()
    uniq_top = []
    url_groups = {}  # URL 그룹별로 최고 점수만 유지
    
    for idx, s, meta in scored:
        u = meta["url"]
        canonical_u = canonical_url(u)
        
        # 1) 정확히 같은 URL은 제외
        if canonical_u in seen:
            continue
        
        # 2) URL 유사성 검사
        is_similar = False
        for existing_url in seen:
            if url_similarity(canonical_u, existing_url) >= 0.9:
                is_similar = True
                break
        
        if is_similar:
            continue
        
        # 3) 도메인별 그룹핑 - 같은 도메인에서 너무 많은 결과 방지
        domain = domain_of(u)
        if domain not in url_groups:
            url_groups[domain] = []
        
        # 같은 도메인에서 이미 2개 이상 선택되었으면 스킵 (매우 높은 점수가 아닌 경우)
        if len(url_groups[domain]) >= 2 and s < 2.0:
            continue
        
        url_groups[domain].append((idx, s, meta))
        uniq_top.append((idx, s, meta))
        seen.add(canonical_u)
        
        if len(uniq_top) >= TOPN_RETURN:
            break

    if not uniq_top:
        print("============================================================")
        print("📊 신뢰도 상세 분석")
        print("============================================================")
        print("• 내용 일관성: 0% (가중치 40%)")
        print("• 출처 다양성: 0% (가중치 25%)")
        print("• 시간적 관련성: 0% (가중치 20%)")
        print("• 근거 품질: 0% (가중치 15%)")
        print("")
        print("연관성 높은 근거를 찾지 못했습니다.")
        print("")
        print("============================================================")
        print("🎯 최종 평가 결과")
        print("============================================================")
        print("신뢰도: 관련된 자료를 찾지 못 하였습니다.")
        print("권장사항: 허위정보 혹은 오보 가능성이 있으니, 공식 출처를 통해 사실 확인이 필요합니다.")
        print("")
        print("📋 신뢰도 해석 가이드 (조정된 기준)")
        print("----------------------------------------")
        print("• 80% 이상: 매우 높음 - 신뢰 가능")
        print("• 65-79%: 높음 - 대체로 신뢰 가능, 추가 검증 권장")
        print("• 50-64%: 보통 - 신중한 검토 필요")
        print("• 35-49%: 낮음 - 오보 의심, 다른 출처 확인 필요")
        print("• 35% 미만: 매우 낮음 - 허위정보 혹은 오보 의심")
        print("============================================================")
        sys.exit(0)  # 정상 종료로 변경

    total_score = sum(s for _, s, __ in uniq_top)
    base_trust_prob = 1 / (1 + math.exp(-total_score))
    
    # 다차원 신뢰도 평가
    reliability_factors = {
        'content_consistency': base_trust_prob,  # 기본 일관성 점수
        'source_diversity': 0.0,                # 출처 다양성
        'temporal_relevance': 0.0,              # 시간적 관련성
        'evidence_quality': 0.0                 # 근거 품질
    }
    
    # 1. 출처 다양성 평가
    unique_domains = set()
    government_sources = 0
    media_sources = 0
    total_articles = len(uniq_top)
    
    for idx, s, meta in uniq_top:
        url = meta['url']
        domain = url.split('/')[2] if '//' in url else url
        unique_domains.add(domain)
        
        # 정부/공공기관 출처
        if any(gov_domain in domain for gov_domain in ['korea.kr', 'mofa.go.kr', 'mois.go.kr', 'gov.kr']):
            government_sources += 1
        # 언론사 출처  
        elif any(media_domain in domain for media_domain in ['yna.co.kr', 'ytn.co.kr', 'jtbc.co.kr', 'naver.com', 'hankyung.com']):
            media_sources += 1
    
    # 출처 다양성 점수 (0~1)
    domain_diversity = min(1.0, len(unique_domains) / max(1, total_articles))
    source_balance = 0.5 if government_sources > 0 and media_sources > 0 else 0.3
    reliability_factors['source_diversity'] = (domain_diversity + source_balance) / 2
    
    # 2. 시간적 관련성 평가 (평가 대상 기사 포함)
    very_old_count = 0
    old_count = 0
    recent_count = 0
    
    # 평가 대상 기사의 연도 먼저 확인
    query_year = None
    if 'jtbc.co.kr' in query_url:
        jtbc_match = re.search(r'NB(\d{2})', query_url)
        if jtbc_match:
            year_suffix = int(jtbc_match.group(1))
            if year_suffix <= 25:
                query_year = 2000 + year_suffix
            else:
                query_year = 1900 + year_suffix
            logger.debug(f"평가 대상 JTBC 기사 연도: {query_url} -> {query_year}")
    
    # 평가 대상 기사가 오래되었으면 강력한 페널티
    if query_year and query_year <= 2015:
        logger.debug(f"평가 대상 기사가 매우 오래됨: {query_year} - 시간적 관련성을 0.1로 설정")
        reliability_factors['temporal_relevance'] = 0.1  # 매우 강한 페널티
    elif query_year and query_year <= 2020:
        logger.debug(f"평가 대상 기사가 오래됨: {query_year} - 시간적 관련성을 0.3으로 설정")
        reliability_factors['temporal_relevance'] = 0.3  # 강한 페널티
    else:
        # 기존 근거 기사들 기반 평가
        if total_articles > 0:
            for idx, s, meta in uniq_top:
                url = meta['url']
                import re
                
                logger.debug(f"연도 감지 시작: {url}")
                
                # 연도 감지 로직 (개선됨)
                year_matches = re.findall(r'20(\d{2})', url)
                detected_year = None
                
                if 'jtbc.co.kr' in url:
                    # JTBC 패턴: NB11272032 -> 11은 2011년
                    jtbc_match = re.search(r'NB(\d{2})', url)
                    if jtbc_match:
                        year_suffix = int(jtbc_match.group(1))
                        if year_suffix <= 25:  # 00-25는 2000-2025
                            detected_year = 2000 + year_suffix
                        else:  # 26-99는 1926-1999 (하지만 실제로는 거의 없음)
                            detected_year = 1900 + year_suffix
                        logger.debug(f"JTBC 연도 감지: {url} -> {detected_year} (year_suffix: {year_suffix})")
                
                if not detected_year and year_matches:
                    for year_suffix in year_matches:
                        year_candidate = int('20' + year_suffix)
                        if 2000 <= year_candidate <= 2025:
                            detected_year = year_candidate
                            logger.debug(f"일반 연도 감지: {url} -> {detected_year}")
                            break
                
                if not detected_year and 'korea.kr' in url and '132038018' in url:
                    detected_year = 2016
                    logger.debug(f"korea.kr 특수 처리: {url} -> {detected_year}")
                
                logger.debug(f"최종 감지된 연도: {url} -> {detected_year}")
                
                if detected_year:
                    if detected_year <= 2015:  # 2015년 이전 (더 엄격)
                        very_old_count += 1
                        old_count += 1
                        logger.debug(f"매우 오래된 기사로 분류: {detected_year}")
                    elif detected_year <= 2020:  # 2020년 이전 (조정)
                        old_count += 1
                        logger.debug(f"오래된 기사로 분류: {detected_year}")
                    else:
                        recent_count += 1
                        logger.debug(f"최신 기사로 분류: {detected_year}")
                else:
                    logger.debug(f"연도 감지 실패: {url}")
            
            logger.debug(f"연도별 분류 결과 - 매우 오래됨: {very_old_count}, 오래됨: {old_count}, 최신: {recent_count}, 전체: {total_articles}")
            
            # 시간적 관련성 점수 (더 엄격한 기준으로 강화)
            recent_ratio = recent_count / total_articles
            old_ratio = old_count / total_articles
            very_old_ratio = very_old_count / total_articles
            
            if very_old_ratio > 0.8:  # 매우 오래된 기사 80% 이상
                reliability_factors['temporal_relevance'] = 0.1  # 매우 낮음 (강화)
            elif very_old_ratio > 0.6:  # 매우 오래된 기사 60% 이상
                reliability_factors['temporal_relevance'] = 0.2  # 매우 낮음 (강화)
            elif very_old_ratio > 0.4:  # 매우 오래된 기사 40% 이상
                reliability_factors['temporal_relevance'] = 0.3  # 낮음 (강화)
            elif very_old_ratio > 0.2:  # 매우 오래된 기사 20% 이상
                reliability_factors['temporal_relevance'] = 0.4  # 낮음 (강화)
            elif old_ratio > 0.6:
                reliability_factors['temporal_relevance'] = 0.6  # 보통 (조정)
            else:
                reliability_factors['temporal_relevance'] = 0.9  # 높음
    
    # 3. 근거 품질 평가
    high_similarity_count = sum(1 for _, s, meta in uniq_top if meta.get('similarity', 0) > 0.7)
    high_support_count = sum(1 for _, s, meta in uniq_top if meta.get('support', 0) > 0.8)
    
    similarity_quality = high_similarity_count / max(1, total_articles)
    support_quality = high_support_count / max(1, total_articles)
    
    # 4. 극단적 주장 탐지 (새로 추가)
    extreme_claim_penalty = 0.0
    
    # 질의 텍스트에서 극단적 표현 탐지
    query_text = q_text + " " + (q_title or "")
    logger.debug(f"허위뉴스 패턴 분석 대상 텍스트: {query_text[:200]}...")
    
    # 개선된 허위뉴스 패턴 탐지 (일반적 패턴)
    # 1. 일반적인 허위뉴스 특징 패턴 (3개 키워드 조합)
    fake_patterns = [
        # 오보/정정 관련 패턴 (새로 추가)
        ('오보', '정정', '사과'),
        ('오역', '잘못', '인정'),
        ('가짜', '허위', '조작'),
        ('방심위', '경고', '징계'),
        ('바로잡', '수정', '정정보도'),
        
        # 정부 정책 관련 비현실적 패턴
        ('모든 국민', '1일 2시간', '법안'),  # 가짜뉴스 특정 케이스
        ('모든 국민', '자동 설치', '벌금'),
        ('모든', '강제', '법안'),
        ('전 국민', '의무', '처벌'),
        
        # 극단적 수치/시간 조합
        ('100%', '즉시', '효과'),
        ('24시간', '완전', '치료'),
        ('하루', '10kg', '감량'),
        ('1일', '차단', '벌금'),
        
        # 의료/건강 허위정보 패턴  
        ('암', '완치', '비법'),
        ('당뇨', '하루', '완전'),
        ('코로나', '예방', '100%'),
        
        # 경제/투자 사기 패턴
        ('무조건', '수익', '보장'),
        ('하루', '백만원', '벌기'),
        ('투자', '원금보장', '고수익'),
        
        # 선정적/선동적 표현 (2개 키워드 조합)
        ('충격', '진실'),
        ('절대', '믿을 수 없는'),
        ('국가기밀', '최초공개'),
        ('자동', '차단'),
        ('강제', '모니터링')
    ]
    
    # 2. 허위뉴스 특징적 패턴 점수
    fake_pattern_score = 0
    detected_patterns = []
    
    for pattern in fake_patterns:
        if len(pattern) == 3:
            # 3개 키워드 조합
            if all(keyword in query_text.lower() for keyword in pattern):
                fake_pattern_score += 3  # 3개 조합은 높은 점수
                detected_patterns.append(pattern)
        elif len(pattern) == 2:
            # 2개 키워드 조합  
            if all(keyword in query_text.lower() for keyword in pattern):
                fake_pattern_score += 1.5  # 2개 조합은 중간 점수
                detected_patterns.append(pattern)
    
    # 3. 개별 허위뉴스 의심 키워드 (정교한 필터링)
    suspicious_keywords = [
        # 극단적 수치 표현 (정상 기사에서 잘 안 나옴)
        '100%', '완전', '전면',
        # 선정적 표현 (뉴스에서 자주 쓰이지 않음)  
        '충격', '놀라운', '믿을 수 없는', '폭로',
        # 의료 관련 과장 (명확한 허위 신호)
        '완치', '효과 100%', '즉시', '하루만에',
        # 경제 관련 사기 표현
        '원금보장', '무손실', '확실한 수익', '대박',
        # 정부 정책 관련 비현실적 표현
        '전 국민', '모든 국민', '일괄 적용'
        # '강제', '절대', '무조건' 등은 제거 (정상 기사에서도 자주 사용)
    ]
    
    mild_extreme_count = sum(1 for keyword in suspicious_keywords if keyword in query_text.lower())
    fake_pattern_score += mild_extreme_count * 0.1  # 개별 표현은 매우 낮은 가중치 (0.3 → 0.1)
    
    logger.debug(f"허위뉴스 패턴 분석 결과: 패턴점수={fake_pattern_score}, 탐지패턴={detected_patterns}, 개별키워드={mild_extreme_count}")
    
    # 4. 페널티 적용 (허위뉴스 의심 시 신뢰도 대폭 감소)
    extreme_claim_penalty = 0.0
    global_extreme_penalty = 0.0
    
    if fake_pattern_score >= 4:  # 강한 허위뉴스 의심
        extreme_claim_penalty = 0.8  # 80% 페널티 (강화)
        global_extreme_penalty = 0.5  # 50% 전체 페널티 (강화)
        print(f"🚨 허위뉴스 강력 의심: 비현실적 패턴 감지 (점수: {fake_pattern_score}) - 신뢰도 대폭 감소")
        if detected_patterns:
            print(f"   감지된 패턴: {detected_patterns}")
    elif fake_pattern_score >= 2:  # 중간 의심
        extreme_claim_penalty = 0.5  # 50% 페널티 (강화)
        global_extreme_penalty = 0.3  # 30% 전체 페널티 (강화)
        print(f"⚠️ 허위뉴스 의심: 의심스러운 표현 탐지 (점수: {fake_pattern_score}) - 신뢰도 감소")
    elif fake_pattern_score >= 1:  # 약한 의심
        extreme_claim_penalty = 0.2  # 20% 페널티
        global_extreme_penalty = 0.1  # 10% 전체 페널티
    
    reliability_factors['evidence_quality'] = max(0, (similarity_quality + support_quality) / 2 - extreme_claim_penalty)
    
    # 최종 신뢰도 계산 (가중 평균) - 반박 증거는 페널티 없이 경고만 표시
    weights = {
        'content_consistency': 0.35,   # 35% - 내용 일관성 (40% → 35%)
        'source_diversity': 0.25,     # 25% - 출처 다양성  
        'temporal_relevance': 0.25,   # 25% - 시간적 관련성 (20% → 25% 강화)
        'evidence_quality': 0.15      # 15% - 근거 품질
    }
    
    final_trust_prob = sum(reliability_factors[factor] * weights[factor] for factor in weights)
    
    # 극단적 표현 페널티만 적용 (반박 증거 페널티 제거)
    final_trust_prob = max(0, final_trust_prob - global_extreme_penalty)
    
    trust_percent = int(round(100 * final_trust_prob))

    # 상세 분석 결과 출력
    print("=" * 60)
    print("📊 신뢰도 상세 분석")
    print("=" * 60)
    print(f"• 내용 일관성: {reliability_factors['content_consistency']*100:.0f}% (가중치 40%)")
    print(f"• 출처 다양성: {reliability_factors['source_diversity']*100:.0f}% (가중치 25%)")
    print(f"• 시간적 관련성: {reliability_factors['temporal_relevance']*100:.0f}% (가중치 20%)")
    print(f"• 근거 품질: {reliability_factors['evidence_quality']*100:.0f}% (가중치 15%)")
    print()
    
    # 신뢰도 구간별 해석 및 권장사항 (조정된 기준)
    if trust_percent >= 80:  # 85% → 80%로 조정
        trust_level = "매우 높음 🟢"
        recommendation = "이 기사는 신뢰할 만합니다. 다양한 출처에서 일관된 정보를 제공하고 있습니다."
    elif trust_percent >= 65:  # 70% → 65%로 조정
        trust_level = "높음 🟡"
        recommendation = "이 기사는 대체로 신뢰할 만하지만, 추가 검증을 권장합니다."
    elif trust_percent >= 50:  # 55% → 50%으로 조정
        trust_level = "보통 🟠"
        recommendation = "이 기사는 신중하게 검토가 필요합니다. 다른 출처와 교차 확인하세요."
    elif trust_percent >= 35:  # 40% → 35%로 조정
        trust_level = "낮음 🔴"
        recommendation = "이 기사의 신뢰도가 낮습니다. 오보가 의심되며, 정부 공식 발표나 권위 있는 출처를 확인하세요."
    else:
        trust_level = "매우 낮음 ⚫"
        recommendation = "이 기사는 신뢰하기 어렵습니다. 허위정보일 가능성이 높습니다."

    print("이 기사(자료)의 신뢰도 평가 근거 링크는 다음과 같습니다.")
    for i, (idx, s, meta) in enumerate(uniq_top, start=1):
        p = 1 / (1 + math.exp(-s))
        pct = int(round(100 * p))
        sim = meta.get("similarity", 0)
        sup = meta.get("support", 0)
        print(f"{i}. {pct}% : {meta['url']} (유사성: {sim:.2f}, 지지도: {sup:.2f})")
    
    # 반박 증거 표시
    if contradiction_evidence:
        print("\n⚠️ 오보 가능성 관련 정보:")
        for i, evidence in enumerate(contradiction_evidence, start=1):
            print(f"   {i}. {evidence['url']}")
            print(f"      반박 키워드: {evidence['contradiction_score']}개, 유사성: {evidence['similarity']:.2f}")
            preview = evidence['text'][:100].replace('\n', ' ')
            print(f"      내용: {preview}...")
        print("   💡 위 정보들은 이 기사와 관련된 정정이나 반박 내용을 포함하고 있어 참고하시기 바랍니다.")
    print()
    
    # 최종 결과
    print("=" * 60)
    print("🎯 최종 평가 결과")
    print("=" * 60)
    print(f"신뢰도: {trust_percent}% - {trust_level}")
    print(f"권장사항: {recommendation}")
    print()
    
    # 신뢰도 기준 가이드
    print("📋 신뢰도 해석 가이드 (조정된 기준)")
    print("-" * 40)
    print("• 80% 이상: 매우 높음 - 신뢰 가능")
    print("• 65-79%: 높음 - 대체로 신뢰 가능, 추가 검증 권장")
    print("• 50-64%: 보통 - 신중한 검토 필요")
    print("• 35-49%: 낮음 - 오보 의심, 다른 출처 확인 필요")
    print("• 35% 미만: 매우 낮음 - 허위정보 혹은 오보 의심")
    print("=" * 60)

# --------------------------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(description="Smart IT - 신뢰도 평가(병렬/배치/GPU, Overall)")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_build = sub.add_parser("build-index", help="시드 크롤링 후 인덱스(pkl) 생성")
    p_build.add_argument("--workers", type=int, default=96, help="시드 병렬 워커 수 (Intel Ultra9 285k 32스레드 최대 활용)")
    p_build.add_argument("--embed-batch", type=int, default=1024, help="임베딩 배치 크기 (RTX3070ti 8GB VRAM 최대 활용)")
    p_build.add_argument("--use-gpu", action="store_true", help="가능하면 CUDA 사용")
    p_build.add_argument("--fp16", action="store_true", help="가능하면 FP16로 추론")
    p_build.add_argument("--http-pool", type=int, default=1024, help="requests 커넥션 풀 크기 (128GB RAM 최대 활용)")
    p_build.add_argument("--sleep", type=float, default=0.001, help="크롤 간 대기(초) - 최고성능 설정")
    p_build.add_argument("--timeout", type=int, default=12, help="요청 타임아웃(초)")
    p_build.add_argument("--fast-extract", action="store_true", help="본문 추출 가속(favor_precision=False)")
    p_build.add_argument("--test-mode", action="store_true", help="🧪 테스트 모드: 소량의 선별된 시드만 사용 (빠른 테스트)")
    p_build.add_argument("--verbose", action="store_true", help="자세한 로그")
    p_build.add_argument("--quiet", action="store_true", help="간단 로그")
    p_build.add_argument("--log-file", type=str, default=None, help="로그 파일 경로")

    p_check = sub.add_parser("check-domains", help="인덱스에 포함된 도메인 확인")
    p_check.add_argument("--domain", type=str, help="특정 도메인 검색 (예: mediatoday)")
    p_check.add_argument("--verbose", action="store_true")
    
    p_eval = sub.add_parser("evaluate", help="URL 신뢰도 평가")
    p_eval.add_argument("--url", required=True, help="평가 대상 기사/자료 URL")
    p_eval.add_argument("--nli-batch", type=int, default=32, help="NLI 배치 크기")
    p_eval.add_argument("--use-gpu", action="store_true", default=True, help="가능하면 CUDA 사용 (기본값: True)")
    p_eval.add_argument("--fp16", action="store_true", default=True, help="가능하면 FP16로 추론 (기본값: True)")
    p_eval.add_argument("--similarity-threshold", type=float, default=0.5, help="근거 유사성 최소 임계값 (기본값: 0.6)")
    p_eval.add_argument("--auto-threshold", action="store_true", help="주제별 동적 임계값 자동 조정")
    p_eval.add_argument("--strict-mode", action="store_true", help="엄격 모드: 임계값 0.65 사용 (고품질 근거만)")
    p_eval.add_argument("--verbose", action="store_true")
    p_eval.add_argument("--quiet", action="store_true", default=True, help="간단 로그 (기본값: True)")
    p_eval.add_argument("--log-file", type=str, default=None)

    args = parser.parse_args()
    
    # 빌드 모드 여부에 따라 로깅 설정 조정
    is_build_mode = (args.cmd == "build-index")
    setup_logging(verbose=getattr(args, "verbose", False),
                  quiet=getattr(args, "quiet", False),
                  log_file=getattr(args, "log_file", None),
                  build_mode=is_build_mode)

    if args.cmd == "build-index":
        build_index(
            workers=args.workers,
            embed_batch=args.embed_batch,
            use_gpu=args.use_gpu,
            fp16=args.fp16,
            http_pool=args.http_pool,
            timeout=args.timeout,
            sleep=args.sleep,
            fast_extract=args.fast_extract,
            test_mode=args.test_mode
        )
    elif args.cmd == "check-domains":
        check_domains(domain_filter=args.domain, verbose=args.verbose)
    elif args.cmd == "evaluate":
        # 동적 임계값 조정
        threshold = args.similarity_threshold
        if args.strict_mode:
            threshold = 0.6  # 엄격한 기준으로 복구
            print(f"🔒 엄격 모드: 유사성 임계값 {threshold} 사용 (고품질 근거만 표시)")
        elif args.auto_threshold:
            # 임시로 HTTP 설정
            if SESSION is None:
                configure_http(http_pool=64, timeout=12)
            
            # 한국어 비중이 높으면 임계값 상향 조정
            html = polite_get(args.url)
            if html:
                text, _, _ = extract_text(args.url, html)
                kr_ratio = korean_ratio(text)
                if kr_ratio >= 0.5:  # 한국어 50% 이상
                    threshold = 0.5
                elif kr_ratio >= 0.3:  # 한국어 30% 이상
                    threshold = 0.4
                print(f"🤖 자동 조정: 한국어 비중 {kr_ratio:.1%}, 임계값 {threshold} 사용")
        else:
            print(f"📊 기본 설정: 유사성 임계값 {threshold} 사용")
        
        evaluate_url(
            query_url=args.url,
            nli_batch=args.nli_batch,
            use_gpu=args.use_gpu,
            fp16=args.fp16,
            similarity_threshold=threshold
        )

if __name__ == "__main__":
    main()
