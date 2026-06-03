(function() {
    if (window.__SNS_V4_FINAL_CORE) return;
    window.__SNS_V4_FINAL_CORE = true;

    const Bridge = {
        log: (m) => { try { AndroidAutomation.log(m); } catch(e) {} },
        onFinished: (s) => { try { AndroidAutomation.onStepFinished(s); } catch(e) {} }
    };

    let config = {};
    let selectors = {};

    window.updateConfig = function(c, s) {
        config = JSON.parse(c);
        selectors = JSON.parse(s);
        Bridge.log("백엔드: 엔진 파라미터 동기화 (자동화: " + config.isRunning + ")");
    };

    function findElement(list) {
        if (!list) return null;
        for (let s of list) {
            try {
                const el = document.querySelector(s);
                if (el && el.offsetParent !== null) return el;
            } catch (e) {}
        }
        return null;
    }

    function triggerAction(el, label) {
        if (!el) return false;
        try {
            el.scrollIntoView({ behavior: 'smooth', block: 'center' });

            setTimeout(() => {
                const clickEvt = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
                el.dispatchEvent(clickEvt);
                el.click();
                Bridge.log("성공: " + label + " 실행 완료");
            }, 800);
            return true;
        } catch (e) {
            Bridge.log("에러: " + e.message);
            return false;
        }
    }

    function handleComment() {
        const text = config.customComment || "정말 멋진 포스팅이네요!";
        const input = findElement(selectors.commentInput);

        if (input) {
            input.scrollIntoView({ behavior: 'smooth', block: 'center' });
            setTimeout(() => {
                if (input.tagName === 'TEXTAREA' || input.tagName === 'INPUT') {
                    input.value = text;
                } else {
                    input.innerText = text;
                }
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));

                setTimeout(() => {
                    const submit = findElement(selectors.commentSubmit);
                    if (submit) {
                        submit.click();
                        Bridge.log("성공: 댓글 주입 및 게시 완료");
                    }
                }, 800);
            }, 1000);
            return true;
        }
        return false;
    }

    window.runAutomationStep = function() {
        if (!config.isRunning) {
            Bridge.log("상태: 엔진 중지 대기");
            return;
        }

        Bridge.log("분석: 최적 타겟 식별 프로세스 가동...");
        let acted = false;

        // 1. 친구 추가
        if (config.autoFriend && !acted) {
            const el = findElement(selectors.friend);
            if (el) acted = triggerAction(el, "친구 추가/팔로우");
        }

        // 2. 좋아요
        if (config.autoLike && !acted) {
            const el = findElement(selectors.like);
            if (el) acted = triggerAction(el, "좋아요");
        }

        // 3. 댓글 작성
        if (config.autoComment && !acted) {
            acted = handleComment();
        }

        if (!acted) {
            Bridge.log("미발견: 신규 타겟 확보를 위해 스크롤...");
            window.scrollBy({ top: 800, behavior: 'smooth' });
            setTimeout(() => Bridge.onFinished(false), 2000);
        } else {
            setTimeout(() => Bridge.onFinished(true), 4000);
        }
    };

    Bridge.log("CORE ENGINE V4 FINAL ONLINE");

    // 자동 시작 트리거 보강
    if (config.isRunning) {
        setTimeout(window.runAutomationStep, 2000);
    }
})();
