# app.py - Smart IT 신뢰도 평가 Flask API
# --------------------------------------------------------------------------------------------
# Flask 웹 API로 신뢰도 평가 기능 제공
# 사용법: python app.py
# API 엔드포인트: POST /evaluate
# --------------------------------------------------------------------------------------------

import json
import sys
import traceback
import math
from datetime import datetime, timezone
from flask import Flask, request, jsonify
from flask_cors import CORS
import logging

# 기존 모듈 임포트
from Veriscope_url import (
    evaluate_url, load_index, configure_http, 
    setup_logging, SESSION, logger
)

app = Flask(__name__)
CORS(app)  # CORS 허용

# 로깅 설정
setup_logging(verbose=False, quiet=True, log_file="api.log")

# 애플리케이션 시작 시 HTTP 세션 설정
configure_http(http_pool=64, timeout=12)

@app.route('/', methods=['GET'])
def home():
    """API 상태 확인"""
    return jsonify({
        "status": "ok",
        "service": "Smart IT 신뢰도 평가 API",
        "version": "1.0",
        "timestamp": datetime.now().isoformat(),
        "endpoints": {
            "evaluate": {
                "method": "POST",
                "url": "/evaluate",
                "description": "뉴스 기사 신뢰도 평가"
            },
            "health": {
                "method": "GET", 
                "url": "/health",
                "description": "서비스 상태 확인"
            }
        }
    })

@app.route('/health', methods=['GET'])
def health_check():
    """헬스체크 엔드포인트"""
    try:
        # 인덱스 파일 존재 확인
        pack = load_index()
        index_size = pack.matrix.shape[0] if pack else 0
        
        return jsonify({
            "status": "healthy",
            "timestamp": datetime.now().isoformat(),
            "index_loaded": True,
            "index_size": index_size,
            "session_configured": SESSION is not None
        })
    except Exception as e:
        return jsonify({
            "status": "unhealthy",
            "timestamp": datetime.now().isoformat(),
            "error": str(e),
            "index_loaded": False
        }), 500

@app.route('/evaluate', methods=['POST'])
def evaluate_news():
    """뉴스 기사 신뢰도 평가 API"""
    try:
        # 요청 데이터 파싱
        data = request.get_json()
        
        if not data:
            return jsonify({
                "success": False,
                "error": "JSON 데이터가 필요합니다."
            }), 400
            
        url = data.get('url')
        if not url:
            return jsonify({
                "success": False,
                "error": "URL이 필요합니다."
            }), 400
            
        # 옵션 파라미터 (기본값 적용)
        nli_batch = data.get('nli_batch', 128)
        use_gpu = data.get('use_gpu', True)
        fp16 = data.get('fp16', True)
        similarity_threshold = data.get('similarity_threshold', 0.6)
        
        logger.info(f"API 요청: {url}")
        
        # 신뢰도 평가 실행
        result = evaluate_url_api(
            query_url=url,
            nli_batch=nli_batch,
            use_gpu=use_gpu,
            fp16=fp16,
            similarity_threshold=similarity_threshold
        )
        
        return jsonify({
            "success": True,
            "timestamp": datetime.now().isoformat(),
            "url": url,
            "result": result
        })
        
    except Exception as e:
        logger.error(f"API 오류: {str(e)}")
        logger.error(traceback.format_exc())
        
        return jsonify({
            "success": False,
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }), 500

def evaluate_url_api(query_url: str, nli_batch: int, use_gpu: bool, fp16: bool, similarity_threshold: float) -> dict:
    """
    API용 신뢰도 평가 함수 - 결과를 JSON 형태로 반환
    """
    from Veriscope_url import (
        domain_of, polite_get, extract_text, make_chunks, 
        get_embedder, load_index, MIN_TEXT_LEN, get_nli,
        summarize_for_nli, TOPK_CANDIDATES, check_keyword_relevance,
        add_url_to_index, save_index, time_weight, source_reputation,
        korean_ratio, FAKE_NEWS_PATTERNS, TOPN_RETURN
    )
    import torch
    from sentence_transformers import util
    import numpy as np
    from collections import defaultdict
    
    if SESSION is None:
        configure_http(http_pool=64, timeout=12)

    pack = load_index()
    embedder, _ = get_embedder(use_gpu=use_gpu, fp16=fp16)

    # URL 파싱 및 콘텐츠 추출
    host = domain_of(query_url)
    html = None
    if host.endswith("n.news.naver.com") or host.endswith("news.naver.com"):
        html = polite_get(query_url, mobile=True) or polite_get(query_url)
    else:
        html = polite_get(query_url) or polite_get(query_url, mobile=True)

    q_text, q_dt, q_title = extract_text(query_url, html)
    if len(q_text) < 50:
        raise ValueError("본문 추출 실패 또는 텍스트가 너무 짧음")

    # 사용자 URL을 인덱스에 추가 (중복이 아닌 경우)
    try:
        if add_url_to_index(query_url, q_text, q_dt, q_title, embedder, pack):
            save_index(pack)
            logger.info("사용자 URL이 인덱스에 추가되었습니다.")
    except Exception as e:
        logger.warning(f"URL 인덱스 추가 중 오류 (계속 진행): {e}")

    q_chunks = make_chunks(q_text, min_len=max(120, MIN_TEXT_LEN // 2))
    if not q_chunks:
        q_chunks = [q_text]

    q_vecs = embedder.encode(q_chunks, convert_to_numpy=True, normalize_embeddings=True)
    sims = util.cos_sim(torch.tensor(q_vecs), torch.from_numpy(pack.matrix)).cpu().numpy()
    sim_per_idx = sims.max(axis=0)

    # 후보 선택
    K = min(TOPK_CANDIDATES, pack.matrix.shape[0])
    cand_idx = np.argsort(-sim_per_idx)[:K].tolist()

    tok, mdl, use_fp16 = get_nli(use_gpu=use_gpu, fp16=fp16)
    q_premise = summarize_for_nli(q_text, max_sents=3)

    # 증거 평가
    evidence_list = []
    seen = set()
    url_groups = defaultdict(list)
    
    for idx in cand_idx:
        rec = pack.records[idx]
        sim_score = float(sim_per_idx[idx])
        
        if sim_score < similarity_threshold:
            continue
            
        # 키워드 관련성 검증
        relevance_info = check_keyword_relevance(q_text, rec.chunk)
        if relevance_info['keyword_match_ratio'] == 0.0:
            continue
            
        # NLI 점수 계산
        if rec.chunk and len(rec.chunk.strip()) > 10:
            hyp = rec.chunk[:500]
            inputs = tok(q_premise, hyp, return_tensors="pt", truncation=True, max_length=512)
            if use_gpu and torch.cuda.is_available():
                inputs = {k: v.cuda() for k, v in inputs.items()}
            
            with torch.no_grad():
                logits = mdl(**inputs).logits
                if use_fp16:
                    logits = logits.float()
                probs = torch.softmax(logits, dim=-1)
                entail_prob = float(probs[0][0])  # ENTAILMENT
        else:
            entail_prob = 0.0
            
        # 관련성 조정
        if not relevance_info['has_good_relevance']:
            if relevance_info['keyword_match_ratio'] < 0.15:
                sim_score *= 0.5
            else:
                sim_score *= 0.7
                
        # 시간/출처 가중치
        dt = datetime.fromtimestamp(rec.published, tz=timezone.utc) if rec.published else None
        time_v = time_weight(dt)
        src_v = source_reputation(rec.url, rec.from_seed)
        
        # 언어 정합 가중치
        kr_ratio_q = korean_ratio(q_text)
        kr_ratio_r = korean_ratio(rec.chunk)
        if kr_ratio_q >= 0.7 and kr_ratio_r >= 0.7:
            lang_bonus = 1.1
        elif kr_ratio_q >= 0.3 and kr_ratio_r >= 0.3:
            lang_bonus = 1.05
        else:
            lang_bonus = 1.0
            
        final_score = sim_score * entail_prob * time_v * src_v * lang_bonus
        
        if final_score >= 0.3:  # 최종 점수 임계값
            evidence_list.append((idx, final_score, {
                'url': rec.url,
                'similarity': sim_score,
                'nli_support': entail_prob,
                'time_weight': time_v,
                'source_weight': src_v,
                'final_score': final_score,
                'title': rec.title,
                'published': rec.published
            }))
    
    # Top 증거 선택 및 중복 제거
    evidence_list.sort(key=lambda x: x[1], reverse=True)
    
    uniq_top = []
    for idx, score, meta in evidence_list:
        canonical_u = meta['url'].lower().rstrip('/')
        if canonical_u in seen:
            continue
            
        domain = domain_of(meta['url'])
        url_groups[domain].append((idx, score, meta))
        uniq_top.append((idx, score, meta))
        seen.add(canonical_u)
        
        if len(uniq_top) >= TOPN_RETURN:
            break
    
    if not uniq_top:
        return {
            "reliability_score": 0,
            "reliability_level": "매우 낮음",
            "status": "연관성 높은 근거를 찾지 못함",
            "evidence": [],
            "factors": {
                "content_consistency": 0,
                "source_diversity": 0,
                "temporal_relevance": 0,
                "evidence_quality": 0
            },
            "recommendation": "허위정보 혹은 오보 의심. 공식 출처를 통해 사실 확인이 필요합니다."
        }
    
    # 신뢰도 계산
    total_score = sum(s for _, s, _ in uniq_top)
    base_trust_prob = 1 / (1 + math.exp(-total_score))
    
    # 다양성 계산
    unique_domains = len(url_groups)
    diversity_factor = min(unique_domains / 3.0, 1.0)
    
    # 시간적 관련성
    if q_dt:
        year_diff = datetime.now().year - q_dt.year
        if year_diff >= 10:
            temporal_relevance = 0.1
        elif year_diff >= 5:
            temporal_relevance = 0.3
        else:
            temporal_relevance = 0.9
    else:
        temporal_relevance = 0.5
        
    # 증거 품질
    avg_nli = sum(meta['nli_support'] for _, _, meta in uniq_top) / len(uniq_top)
    evidence_quality = avg_nli
    
    # 최종 신뢰도 (가중평균)
    reliability_factors = {
        'content_consistency': base_trust_prob,
        'source_diversity': diversity_factor, 
        'temporal_relevance': temporal_relevance,
        'evidence_quality': evidence_quality
    }
    
    weights = {'content_consistency': 0.4, 'source_diversity': 0.25, 'temporal_relevance': 0.2, 'evidence_quality': 0.15}
    final_reliability = sum(reliability_factors[k] * weights[k] for k in weights)
    final_reliability_pct = int(final_reliability * 100)
    
    # 신뢰도 레벨 결정
    if final_reliability_pct >= 80:
        level = "매우 높음"
        recommendation = "이 기사는 신뢰할 만합니다. 다양한 출처에서 일관된 정보를 제공하고 있습니다."
    elif final_reliability_pct >= 65:
        level = "높음"
        recommendation = "이 기사는 대체로 신뢰할 만하지만, 추가 검증을 권장합니다."
    elif final_reliability_pct >= 50:
        level = "보통"
        recommendation = "이 기사는 신중하게 검토가 필요합니다. 다른 출처와 교차 확인하세요."
    elif final_reliability_pct >= 35:
        level = "낮음"
        recommendation = "이 기사의 신뢰도가 낮습니다. 정부 공식 발표나 권위 있는 출처를 확인하세요."
    else:
        level = "매우 낮음"
        recommendation = "허위정보 혹은 오보 의심. 공식 출처를 통해 사실 확인이 필요합니다."
    
    # 증거 목록 구성
    evidence_data = []
    for idx, score, meta in uniq_top:
        evidence_data.append({
            "url": meta['url'],
            "title": meta['title'],
            "reliability_score": int(score * 100),
            "similarity": round(meta['similarity'], 3),
            "nli_support": round(meta['nli_support'], 3),
            "published": datetime.fromtimestamp(meta['published']).isoformat() if meta['published'] else None
        })
    
    return {
        "reliability_score": final_reliability_pct,
        "reliability_level": level,
        "status": "평가 완료",
        "evidence": evidence_data,
        "factors": {
            "content_consistency": int(reliability_factors['content_consistency'] * 100),
            "source_diversity": int(reliability_factors['source_diversity'] * 100), 
            "temporal_relevance": int(reliability_factors['temporal_relevance'] * 100),
            "evidence_quality": int(reliability_factors['evidence_quality'] * 100)
        },
        "recommendation": recommendation,
        "article_info": {
            "title": q_title,
            "published": q_dt.isoformat() if q_dt else None,
            "domain": domain_of(query_url)
        }
    }

if __name__ == '__main__':
    print("🚀 Smart IT 신뢰도 평가 API 서버 시작...")
    print("📡 엔드포인트:")
    print("  - GET  /        : API 정보")
    print("  - GET  /health  : 헬스체크") 
    print("  - POST /evaluate: 신뢰도 평가")
    print()
    print("📖 사용 예시:")
    print('curl -X POST http://localhost:5000/evaluate \\')
    print('  -H "Content-Type: application/json" \\')
    print('  -d \'{"url": "https://news.example.com/article/123"}\'')
    print()
    
    # 개발 서버 실행
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)