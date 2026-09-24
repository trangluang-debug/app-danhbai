let stompClient = null;
let lastGameState = null;
let selectedIndices = []; // Lưu index các lá bài đang chọn trên tay

function connect() {
    const socket = new SockJS('/ws-game');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function () {
        stompClient.subscribe('/topic/game-state', function (response) {
            const state = JSON.parse(response.body);
            renderGameState(state);
        });
        // Đồng bộ mode ban đầu sau khi kết nối
        switchGame();
    });
}

function switchGame() {
    const gameType = document.getElementById('gameSelect').value;
    const bluffPanel = document.getElementById('bluffPanel');
    if (bluffPanel) {
        if (gameType === 'BLUFF') {
            bluffPanel.style.display = 'flex';
            bluffPanel.classList.add('active');
        } else {
            bluffPanel.style.display = 'none';
            bluffPanel.classList.remove('active');
        }
    }
    if (stompClient) {
        stompClient.send("/app/select-game", {}, JSON.stringify({ gameType: gameType }));
    }
}

function startGame() {
    if (stompClient) {
        selectedIndices = [];
        closeModal();
        stompClient.send("/app/start", {}, JSON.stringify({ numBots: 3 }));
    }
}

function playSelectedCards() {
    if (selectedIndices.length === 0) {
        alert("Vui lòng chọn lá bài trước khi đánh!");
        return;
    }

    const gameType = document.getElementById('gameSelect').value;

    // Chỉ chặn 4 lá khi thực sự là BLUFF
    if (gameType === 'BLUFF' && selectedIndices.length > 4) {
        alert("Chế độ Nói Láo chỉ cho phép úp tối đa 4 lá bài một lượt!");
        return;
    }

    const me = lastGameState && lastGameState.players ? lastGameState.players.find(p => p.id === 'p1') : null;
    const hand = me && me.hand ? me.hand : [];
    const selectedCards = selectedIndices.map(idx => hand[idx]).filter(c => c !== undefined);

    if (selectedCards.length === 0) {
        alert("Lá bài chọn không hợp lệ!");
        return;
    }

    const claimedRankInput = document.getElementById('claimedRankInput') || document.getElementById('claimedRankSelect');
    const claimedRankVal = claimedRankInput ? parseInt(claimedRankInput.value) : 3;

    const payload = {
        playerId: 'p1',
        cards: selectedCards,
        claimedRank: (gameType === 'BLUFF') ? (isNaN(claimedRankVal) ? 3 : claimedRankVal) : null
    };

    if (stompClient) {
        stompClient.send("/app/play", {}, JSON.stringify(payload));
        selectedIndices = [];
    }
}

function passTurn() {
    if (stompClient) {
        stompClient.send("/app/pass", {}, JSON.stringify({ playerId: 'p1' }));
        selectedIndices = [];
    }
}

function challengeBluff() {
    if (stompClient) {
        stompClient.send("/app/challenge", {}, JSON.stringify({ playerId: 'p1' }));
    }
}

function renderGameState(state) {
    lastGameState = state;
    if (!state) return;

    const msgElem = document.getElementById('gameMessage');
    if (msgElem) msgElem.innerText = state.gameMessage || '';

    // Cập nhật hiển thị Bluff Panel nếu ở chế độ Bluff
    const isBluffMode = state.gameType === 'BLUFF' || document.getElementById('gameSelect').value === 'BLUFF';
    const bluffPanel = document.getElementById('bluffPanel');
    if (bluffPanel) {
        bluffPanel.style.display = isBluffMode ? 'flex' : 'none';
        if (isBluffMode) {
            const pileCountElem = document.getElementById('penaltyPileCount');
            const rankElem = document.getElementById('currentClaimedRank');
            const expCountElem = document.getElementById('currentExpectedCount');
            if (pileCountElem) pileCountElem.innerText = state.penaltyPileCount ?? 0;
            if (rankElem) rankElem.innerText = formatRank(state.currentClaimedRank);
            if (expCountElem) expCountElem.innerText = state.currentExpectedCount ?? 0;

            const rankInput = document.getElementById('claimedRankInput') || document.getElementById('claimedRankSelect');
            if (rankInput) {
                if (state.currentClaimedRank && state.currentClaimedRank > 0) {
                    rankInput.value = state.currentClaimedRank;
                    rankInput.disabled = true;
                } else {
                    rankInput.disabled = false;
                }
            }
        }
    }

    const me = state.players ? state.players.find(p => p.id === 'p1') : null;
    const myHand = me && me.hand ? me.hand : [];

    selectedIndices = selectedIndices.filter(i => i < myHand.length);

    // 1. Render bài người chơi (Player 1)
    renderMyHand(myHand);

    // 2. Render bài đánh giữa bàn
    const playedCardsDiv = document.getElementById('playedCards');
    if (playedCardsDiv) {
        playedCardsDiv.innerHTML = '';
        if (state.lastPlayedCards && state.lastPlayedCards.length > 0) {
            state.lastPlayedCards.forEach(card => {
                const cardElem = document.createElement('div');
                cardElem.className = 'card center-card';
                if (card.suit === 3 || card.suit === 4) cardElem.classList.add('red');
                cardElem.innerText = formatCard(card);
                playedCardsDiv.appendChild(cardElem);
            });
        }
    }

    // 3. Render thông tin & bài úp của Bot
    if (state.players) {
        state.players.forEach((p, index) => {
            const slot = document.getElementById(p.id);
            if (slot) {
                if (index === state.currentTurnIndex && !state.gameResult) {
                    slot.classList.add('active-turn');
                } else {
                    slot.classList.remove('active-turn');
                }

                const actionElem = slot.querySelector('.last-action');
                if (actionElem) {
                    actionElem.innerText = p.lastAction || '';
                    actionElem.className = p.lastAction === 'Bỏ lượt' ? 'last-action pass-text' : 'last-action play-text';
                }

                const countElem = slot.querySelector('.cards-count');
                if (countElem) {
                    countElem.innerText = `Lá bài: ${p.hand ? p.hand.length : 0}`;
                }

                if (p.id !== 'p1') {
                    let botCardsContainer = slot.querySelector('.bot-cards');
                    if (!botCardsContainer) {
                        botCardsContainer = document.createElement('div');
                        botCardsContainer.className = 'bot-cards';
                        slot.appendChild(botCardsContainer);
                    }
                    botCardsContainer.innerHTML = '';
                    const cardCount = p.hand ? p.hand.length : 0;
                    for (let i = 0; i < cardCount; i++) {
                        const backCard = document.createElement('div');
                        backCard.className = 'card-back';
                        botCardsContainer.appendChild(backCard);
                    }
                }
            }
        });
    }

    // 4. Modal Kết quả
    if (state.gameResult === 'WIN') {
        showModal('🎉 BẠN ĐÃ THẮNG!', 'Chúc mừng bạn đã đánh hết bài!', 'win');
    } else if (state.gameResult === 'LOSE') {
        showModal('☠️ BẠN ĐÃ THUA!', state.gameMessage || 'Máy đã thắng trước!', 'lose');
    }
}

function renderMyHand(handCards) {
    const myHandDiv = document.getElementById('myHand');
    if (!myHandDiv) return;
    myHandDiv.innerHTML = '';

    const totalCards = handCards.length;
    if (totalCards === 0) return;

    const cardWidth = 58;
    const containerWidth = myHandDiv.clientWidth || 650;
    let marginLeft = -18;

    if (totalCards > 1) {
        const maxOverlapNeeded = (totalCards * cardWidth - containerWidth) / (totalCards - 1);
        if (maxOverlapNeeded > 0) {
            const overlap = Math.min(maxOverlapNeeded + 12, cardWidth - 18);
            marginLeft = -overlap;
        }
    }

    handCards.forEach((card, index) => {
        const cardElem = document.createElement('div');
        cardElem.className = 'card';
        cardElem.innerText = formatCard(card);

        if (card.suit === 3 || card.suit === 4) {
            cardElem.classList.add('red');
        }

        if (index > 0) {
            cardElem.style.marginLeft = `${marginLeft}px`;
        } else {
            cardElem.style.marginLeft = '0px';
        }

        if (selectedIndices.includes(index)) {
            cardElem.classList.add('selected');
        }

        cardElem.onclick = () => toggleSelectCard(card, cardElem, index);
        myHandDiv.appendChild(cardElem);
    });
}

function toggleSelectCard(card, element, index) {
    const gameType = document.getElementById('gameSelect').value;
    const pos = selectedIndices.indexOf(index);

    if (pos > -1) {
        // Nếu đã chọn rồi thì bỏ chọn
        selectedIndices.splice(pos, 1);
        element.classList.remove('selected');
    } else {
        // QUAN TRỌNG: CHỈ CHẶN KHI ĐANG Ở CHẾ ĐỘ NÓI LÁO (BLUFF)
        if (gameType === 'BLUFF' && selectedIndices.length >= 4) {
            alert('Chế độ Nói Láo chỉ cho phép chọn tối đa 4 lá bài mỗi lượt!');
            return;
        }

        // Nếu đang ở Tiến Lên (TIEN_LEN), cho phép chọn thoải mái không giới hạn số lá
        selectedIndices.push(index);
        element.classList.add('selected');
    }
}

function formatCard(card) {
    if (!card) return '';
    const suits = { 1: '♠', 2: '♣', 3: '♦', 4: '♥' };
    const ranks = { 3:'3', 4:'4', 5:'5', 6:'6', 7:'7', 8:'8', 9:'9', 10:'10', 11:'J', 12:'Q', 13:'K', 14:'A', 15:'2', 0: 'Joker' };
    return card.rank === 0 ? '🃏' : `${ranks[card.rank] || card.rank}${suits[card.suit] || ''}`;
}

function formatRank(rank) {
    if (!rank || rank === -1) return '-';
    const ranks = { 3:'3', 4:'4', 5:'5', 6:'6', 7:'7', 8:'8', 9:'9', 10:'10', 11:'J', 12:'Q', 13:'K', 14:'A', 15:'2' };
    return ranks[rank] || rank;
}

function showModal(title, desc, type) {
    const modal = document.getElementById('resultModal');
    const modalTitle = document.getElementById('resultTitle');
    const modalDesc = document.getElementById('resultDesc');
    if (!modal) return;

    modalTitle.innerText = title;
    modalDesc.innerText = desc;
    modalTitle.style.color = type === 'win' ? '#2ecc71' : '#e74c3c';

    modal.classList.remove('hidden');
}

function closeModal() {
    const modal = document.getElementById('resultModal');
    if (modal) modal.classList.add('hidden');
}

function closeModalAndRestart() {
    closeModal();
    startGame();
}

window.onload = connect;