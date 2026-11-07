-- 설문 질문·선택지 데이터 (화면설계 스크린샷에서 옮김)
-- schema.sql 을 먼저 실행한 뒤 실행한다. 여러 번 실행해도 이미 있는 줄은 건너뛴다 (ON CONFLICT DO NOTHING)
-- 문구를 고칠 때는 이 파일과 DB 를 함께 고친다 (이미 들어간 줄은 다시 실행해도 바뀌지 않으므로 UPDATE 를 따로 실행)

-- ---------------------------
-- 설문 2개
-- ---------------------------
INSERT INTO survey (code, title, duration_text, finish_label, is_numbered) VALUES
    ('diagnosis', '1분 진단', NULL, '내 유형으로 캠핑장 보기', true),
    ('travel', '여행 취향', '약 30초', '취향 저장하고 관광지 보기', false)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------
-- 단계 (화면 한 장씩)
-- ---------------------------
INSERT INTO survey_step (survey_id, step, title, sub, note)
SELECT s.id, v.step, v.title, v.sub, v.note
  FROM survey s,
       (VALUES
        ('diagnosis', 1, '어떻게 캠핑하세요?', '지역 · 날짜 · 동행은 여행마다 바뀌니까 나중에 지도에서 골라요', NULL),
        ('diagnosis', 2, '밤에 어떻게 지내세요?', '사고가 가장 많이 나는 시간이라 꼭 물어봐요', NULL),
        ('diagnosis', 3, '가까이 있으면 안심되는 곳은요?', '이 답이 캠핑장 추천 순서에 가장 크게 반영돼요', NULL),
        ('travel', 1, '캠핑 가서 뭘 하고 싶으세요?', '고른 취향으로 관광지 순서를 바꿔드려요. 캠핑장 안전점수에는 영향이 없어요.',
         '아이와 함께라면 나이에 맞지 않는 활동(예: 래프팅 연령 제한)은 뒤로 보내드려요. 아이 정보는 내 정보 › 함께 가는 가족에서 바꿀 수 있어요.')
       ) AS v (survey_code, step, title, sub, note)
 WHERE s.code = v.survey_code
ON CONFLICT (survey_id, step) DO NOTHING;

-- ---------------------------
-- 질문
-- ---------------------------
INSERT INTO survey_question (step_id, code, sort_order, title, input_type, max_select, note)
SELECT st.id, v.code, v.sort_order, v.title, v.input_type, v.max_select, v.note
  FROM survey s
  JOIN survey_step st ON st.survey_id = s.id,
       (VALUES
        ('diagnosis', 1, 'camp_type',   1, '어떤 캠핑을 하나요?',      'card', 1, NULL),
        ('diagnosis', 1, 'transport',   2, '어떻게 이동하나요?',       'chip', 1, NULL),
        ('diagnosis', 2, 'heating',     1, '어떤 난방을 쓰나요?',      'card', 0, NULL),
        ('diagnosis', 2, 'safety_item', 2, '챙겨둔 안전 용품은?',      'chip', 0, NULL),
        ('diagnosis', 3, 'nearby',      1, '안심되는 주변 시설은?',    'rank', 3, '1순위와 가까운 캠핑장일수록 지도에서 더 안심으로 보여요.'),
        ('travel',    1, 'activity',    1, '가고 싶은 관광지 · 체험은?', 'chip', 3, NULL),
        ('travel',    1, 'nature',      2, '어떤 자연환경이 좋나요?',  'chip', 2, NULL)
       ) AS v (survey_code, step, code, sort_order, title, input_type, max_select, note)
 WHERE s.code = v.survey_code AND st.step = v.step
ON CONFLICT (step_id, code) DO NOTHING;

-- ---------------------------
-- 선택지
-- ---------------------------
INSERT INTO survey_option (question_id, code, sort_order, label, description, is_exclusive, warning_text)
SELECT q.id, v.code, v.sort_order, v.label, v.description, v.is_exclusive, v.warning_text
  FROM survey s
  JOIN survey_step st ON st.survey_id = s.id
  JOIN survey_question q ON q.step_id = st.id,
       (VALUES
        -- 1분 진단 1단계
        ('diagnosis', 'camp_type', 'auto',        1, '오토캠핑장',      '차 옆에 텐트를 쳐요 · 차박 포함', false, NULL),
        ('diagnosis', 'camp_type', 'glamping',    2, '글램핑 · 카라반', '장비 없이 시설을 이용해요',       false, NULL),
        ('diagnosis', 'camp_type', 'backpacking', 3, '노지 · 백패킹',   '캠핑이 허용된 구역만 안내해요',   false, NULL),
        ('diagnosis', 'camp_type', 'unknown',     4, '모르겠어요',      '안전 기준에 맞춰 정해드려요',     false, NULL),
        ('diagnosis', 'transport', 'car',         1, '자동차',   NULL, false, NULL),
        ('diagnosis', 'transport', 'public',      2, '대중교통', NULL, false, NULL),
        ('diagnosis', 'transport', 'etc',         3, '그 외',    '자전거 · 도보 · 바이크', false, NULL),
        -- 1분 진단 2단계 (연료를 태우는 난방은 일산화탄소 경고)
        ('diagnosis', 'heating', 'electric', 1, '전기장판 · 전기히터', NULL, false, NULL),
        ('diagnosis', 'heating', 'gas',      2, '가스 · 석유난로',     NULL, false, '연료를 태우는 난방은 텐트 안에서 일산화탄소 위험이 있어요. 결과에서 가장 먼저 알려드려요.'),
        ('diagnosis', 'heating', 'wood',     3, '화목난로 · 숯불',     NULL, false, '연료를 태우는 난방은 텐트 안에서 일산화탄소 위험이 있어요. 결과에서 가장 먼저 알려드려요.'),
        ('diagnosis', 'heating', 'none',     4, '난방은 하지 않아요',  NULL, true,  NULL),
        ('diagnosis', 'safety_item', 'extinguisher', 1, '소화기',            NULL, false, NULL),
        ('diagnosis', 'safety_item', 'first_aid',    2, '구급상자',          NULL, false, NULL),
        ('diagnosis', 'safety_item', 'co_alarm',     3, '일산화탄소 경보기', NULL, false, NULL),
        ('diagnosis', 'safety_item', 'lantern',      4, '랜턴 · 호루라기',   NULL, false, NULL),
        ('diagnosis', 'safety_item', 'battery',      5, '보조배터리',        NULL, false, NULL),
        ('diagnosis', 'safety_item', 'nothing',      6, '아직 없어요',       NULL, true,  NULL),
        -- 1분 진단 3단계
        ('diagnosis', 'nearby', 'er',     1, '응급실',             NULL, false, NULL),
        ('diagnosis', 'nearby', 'fire',   2, '소방서 · 119 구조대', NULL, false, NULL),
        ('diagnosis', 'nearby', 'police', 3, '지구대 · 파출소',     NULL, false, NULL),
        ('diagnosis', 'nearby', 'store',  4, '편의점',             NULL, false, NULL),
        ('diagnosis', 'nearby', 'toilet', 5, '공중화장실',         NULL, false, NULL),
        ('diagnosis', 'nearby', 'cctv',   6, 'CCTV',               NULL, false, NULL),
        ('diagnosis', 'nearby', 'light',  7, '보안등',             NULL, false, NULL),
        ('diagnosis', 'nearby', 'house',  8, '인근 주택',          NULL, false, NULL),
        -- 여행 취향
        ('travel', 'activity', 'trekking', 1, '자연 · 트레킹', NULL, false, NULL),
        ('travel', 'activity', 'water',    2, '물놀이 · 레저', NULL, false, NULL),
        ('travel', 'activity', 'activity', 3, '액티비티',      NULL, false, NULL),
        ('travel', 'activity', 'food',     4, '맛집 · 시장',   NULL, false, NULL),
        ('travel', 'activity', 'culture',  5, '문화 · 전시',   NULL, false, NULL),
        ('travel', 'activity', 'stars',    6, '별 보기 · 야경', NULL, false, NULL),
        ('travel', 'activity', 'festival', 7, '축제 · 공연',   NULL, false, NULL),
        ('travel', 'activity', 'rest',     8, '쉬기만',        NULL, false, NULL),
        ('travel', 'nature', 'mountain', 1, '산 · 숲',     NULL, false, NULL),
        ('travel', 'nature', 'valley',   2, '계곡 · 강',   NULL, false, NULL),
        ('travel', 'nature', 'lake',     3, '호수',        NULL, false, NULL),
        ('travel', 'nature', 'sea',      4, '바다 · 해변', NULL, false, NULL),
        ('travel', 'nature', 'field',    5, '평지 · 잔디', NULL, false, NULL)
       ) AS v (survey_code, question_code, code, sort_order, label, description, is_exclusive, warning_text)
 WHERE s.code = v.survey_code AND q.code = v.question_code
ON CONFLICT (question_id, code) DO NOTHING;
