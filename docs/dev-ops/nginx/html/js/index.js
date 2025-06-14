const chatArea = document.getElementById('chatArea');
const messageInput = document.getElementById('messageInput');
const submitBtn = document.getElementById('submitBtn');
const newChatBtn = document.getElementById('newChatBtn');
const chatList = document.getElementById('chatList');
const welcomeMessage = document.getElementById('welcomeMessage');
const toggleSidebarBtn = document.getElementById('toggleSidebar');
const sidebar = document.getElementById('sidebar');
let currentEventSource = null;
let currentChatId = null;

// Load knowledge base list
document.addEventListener('DOMContentLoaded', function () {
    const loadRagOptions = () => {
        const ragSelect = document.getElementById('ragSelect');

        fetch('http://localhost:8090/api/v1/rag/query_rag_tag_list')
            .then(response => response.json())
            .then(data => {
                if (data.code === '0000' && data.data) {
                    // Clear existing options (keep the first default option)
                    while (ragSelect.options.length > 1) {
                        ragSelect.remove(1);
                    }

                    // Add new options
                    data.data.forEach(tag => {
                        const option = new Option(`Rag: ${tag}`, tag);
                        ragSelect.add(option);
                    });
                }
            })
            .catch(error => {
                console.error('Failed to get knowledge base list:', error);
            });
    };

    // Initial load
    loadRagOptions();
});

function createNewChat() {
    const chatId = Date.now().toString();
    currentChatId = chatId;
    localStorage.setItem('currentChatId', chatId);
    localStorage.setItem(`chat_${chatId}`, JSON.stringify({
        name: 'New Chat',
        messages: []
    }));
    updateChatList();
    clearChatArea();
}

function deleteChat(chatId) {
    if (confirm('Are you sure you want to delete this chat?')) {
        localStorage.removeItem(`chat_${chatId}`);
        if (currentChatId === chatId) {
            createNewChat();
        }
        updateChatList();
    }
}

function updateChatList() {
    chatList.innerHTML = '';
    const chats = Object.keys(localStorage).filter(key => key.startsWith('chat_'));

    const currentChatIndex = chats.findIndex(key => key.split('_')[1] === currentChatId);
    if (currentChatIndex !== -1) {
        const currentChat = chats[currentChatIndex];
        chats.splice(currentChatIndex, 1);
        chats.unshift(currentChat);
    }

    chats.forEach(chatKey => {
        let chatData = JSON.parse(localStorage.getItem(chatKey));
        const chatId = chatKey.split('_')[1];

        // Migrate old format (array) to new object format
        if (Array.isArray(chatData)) {
            chatData = {
                name: `Chat ${new Date(parseInt(chatId)).toLocaleDateString()}`,
                messages: chatData
            };
            localStorage.setItem(chatKey, JSON.stringify(chatData));
        }

        const li = document.createElement('li');
        li.className = `chat-item flex items-center justify-between p-2 hover:bg-gray-100 rounded-lg cursor-pointer transition-colors ${chatId === currentChatId ? 'bg-blue-50' : ''}`;
        li.innerHTML = `
            <div class="flex-1">
                <div class="text-sm font-medium">${chatData.name}</div>
                <div class="text-xs text-gray-400">${new Date(parseInt(chatId)).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })}</div>
            </div>
            <div class="chat-actions flex items-center gap-1 opacity-0 transition-opacity duration-200">
                <button class="p-1 hover:bg-gray-200 rounded text-gray-500" onclick="renameChat('${chatId}')">Rename</button>
                <button class="p-1 hover:bg-red-200 rounded text-red-500" onclick="deleteChat('${chatId}')">Delete</button>
            </div>
        `;
        li.addEventListener('click', (e) => {
            if (!e.target.closest('.chat-actions')) {
                loadChat(chatId);
            }
        });
        li.addEventListener('mouseenter', () => {
            li.querySelector('.chat-actions').classList.remove('opacity-0');
        });
        li.addEventListener('mouseleave', () => {
            li.querySelector('.chat-actions').classList.add('opacity-0');
        });
        chatList.appendChild(li);
    });
}

let currentContextMenu = null;
// Optimized context menu
function showChatContextMenu(event, chatId) {
    event.stopPropagation();
    closeContextMenu();

    const buttonRect = event.target.closest('button').getBoundingClientRect();
    const menu = document.createElement('div');
    menu.className = 'context-menu';
    menu.style.position = 'fixed';
    menu.style.left = `${buttonRect.left}px`;
    menu.style.top = `${buttonRect.bottom + 4}px`;

    menu.innerHTML = `
        <div class="context-menu-item" onclick="renameChat('${chatId}')">Rename</div>
        <div class="context-menu-item text-red-500" onclick="deleteChat('${chatId}')">Delete</div>
    `;

    document.body.appendChild(menu);
    currentContextMenu = menu;

    setTimeout(() => {
        document.addEventListener('click', closeContextMenu, { once: true });
    });
}

function closeContextMenu() {
    if (currentContextMenu) {
        currentContextMenu.remove();
        currentContextMenu = null;
    }
}

function renameChat(chatId) {
    const chatKey = `chat_${chatId}`;
    const chatData = JSON.parse(localStorage.getItem(chatKey));
    const currentName = chatData.name || `Chat ${new Date(parseInt(chatId)).toLocaleString()}`;
    const newName = prompt('Enter new chat name', currentName);

    if (newName) {
        chatData.name = newName;
        localStorage.setItem(chatKey, JSON.stringify(chatData));
        updateChatList();
    }
}

function loadChat(chatId) {
    currentChatId = chatId;
    localStorage.setItem('currentChatId', chatId);
    clearChatArea();
    const chatData = JSON.parse(localStorage.getItem(`chat_${chatId}`) || '{ "messages": [] }');
    chatData.messages.forEach(msg => {
        appendMessage(msg.content, msg.isAssistant, false);
    });
    updateChatList();
}

function clearChatArea() {
    chatArea.innerHTML = '';
    welcomeMessage.style.display = 'flex';
}

function appendMessage(content, isAssistant = false, saveToStorage = true) {
    welcomeMessage.style.display = 'none';
    const messageDiv = document.createElement('div');
    messageDiv.className = `max-w-4xl mx-auto mb-4 p-4 rounded-lg ${isAssistant ? 'bg-gray-100' : 'bg-white border'} markdown-body relative`;

    const renderedContent = DOMPurify.sanitize(marked.parse(content));
    messageDiv.innerHTML = renderedContent;

    const copyBtn = document.createElement('button');
    copyBtn.className = 'absolute top-2 right-2 p-1 bg-gray-200 rounded-md text-xs';
    copyBtn.textContent = 'Copy';
    copyBtn.onclick = () => {
        navigator.clipboard.writeText(content).then(() => {
            copyBtn.textContent = 'Copied';
            setTimeout(() => copyBtn.textContent = 'Copy', 2000);
        });
    };
    messageDiv.appendChild(copyBtn);

    chatArea.appendChild(messageDiv);
    chatArea.scrollTop = chatArea.scrollHeight;

    if (saveToStorage && currentChatId) {
        const chatData = JSON.parse(localStorage.getItem(`chat_${currentChatId}`) || '{"name": "New Chat", "messages": []}');
        chatData.messages.push({ content, isAssistant });
        localStorage.setItem(`chat_${currentChatId}`, JSON.stringify(chatData));
    }
}

function startEventStream(message) {
    if (currentEventSource) currentEventSource.close();

    const ragTag = document.getElementById('ragSelect').value;
    const aiModelSelect = document.getElementById('aiModel');
    const aiModelValue = aiModelSelect.value;
    const aiModelModel = aiModelSelect.options[aiModelSelect.selectedIndex].getAttribute('model');

    let url;
    if (ragTag) {
        url = `http://localhost:8090/api/v1/${aiModelValue}/generate_stream_rag?message=${encodeURIComponent(message)}&ragTag=${encodeURIComponent(ragTag)}&model=${encodeURIComponent(aiModelModel)}`;
    } else {
        url = `http://localhost:8090/api/v1/${aiModelValue}/generate_stream?message=${encodeURIComponent(message)}&model=${encodeURIComponent(aiModelModel)}`;
    }

    currentEventSource = new EventSource(url);
    let accumulatedContent = '';
    let tempMessageDiv = null;

    currentEventSource.onmessage = function (event) {
        try {
            const data = JSON.parse(event.data);

            if (data.result?.output?.content) {
                const newContent = data.result.output.content;
                accumulatedContent += newContent;

                if (!tempMessageDiv) {
                    tempMessageDiv = document.createElement('div');
                    tempMessageDiv.className = 'max-w-4xl mx-auto mb-4 p-4 rounded-lg bg-gray-100 markdown-body relative';
                    chatArea.appendChild(tempMessageDiv);
                    welcomeMessage.style.display = 'none';
                }

                tempMessageDiv.textContent = accumulatedContent;
                chatArea.scrollTop = chatArea.scrollHeight;
            }

            if (data.result?.output?.properties?.finishReason === 'STOP') {
                currentEventSource.close();
                const finalContent = accumulatedContent;
                tempMessageDiv.innerHTML = DOMPurify.sanitize(marked.parse(finalContent));

                const copyBtn = document.createElement('button');
                copyBtn.className = 'absolute top-2 right-2 p-1 bg-gray-200 rounded-md text-xs';
                copyBtn.textContent = 'Copy';
                copyBtn.onclick = () => {
                    navigator.clipboard.writeText(finalContent).then(() => {
                        copyBtn.textContent = 'Copied';
                        setTimeout(() => copyBtn.textContent = 'Copy', 2000);
                    });
                };
                tempMessageDiv.appendChild(copyBtn);

                if (currentChatId) {
                    const chatData = JSON.parse(localStorage.getItem(`chat_${currentChatId}`) || '{"name": "New Chat", "messages": []}');
                    chatData.messages.push({ content: finalContent, isAssistant: true });
                    localStorage.setItem(`chat_${currentChatId}`, JSON.stringify(chatData));
                }
            }
        } catch (e) {
            console.error('Error parsing event data:', e);
        }
    };

    currentEventSource.onerror = function (error) {
        console.error('EventSource error:', error);
        currentEventSource.close();
    };
}

submitBtn.addEventListener('click', () => {
    const message = messageInput.value.trim();
    if (!message) return;

    if (!currentChatId) {
        createNewChat();
    }

    appendMessage(message, false);
    messageInput.value = '';
    startEventStream(message);
});

messageInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        submitBtn.click();
    }
});

newChatBtn.addEventListener('click', createNewChat);

toggleSidebarBtn.addEventListener('click', () => {
    sidebar.classList.toggle('-translate-x-full');
    updateSidebarIcon();
});

function updateSidebarIcon() {
    const iconPath = document.getElementById('sidebarIconPath');
    if (sidebar.classList.contains('-translate-x-full')) {
        iconPath.setAttribute('d', 'M4 6h16M4 12h4m12 0h-4M4 18h16');
    } else {
        iconPath.setAttribute('d', 'M4 6h16M4 12h16M4 18h16');
    }
}

// Init
updateChatList();
const savedChatId = localStorage.getItem('currentChatId');
if (savedChatId) {
    loadChat(savedChatId);
}

// Responsive layout adjustments
window.addEventListener('resize', () => {
    if (window.innerWidth > 768) {
        sidebar.classList.remove('-translate-x-full');
    } else {
        sidebar.classList.add('-translate-x-full');
    }
});

if (window.innerWidth <= 768) {
    sidebar.classList.add('-translate-x-full');
}

updateSidebarIcon();

// Upload menu dropdown control
const uploadMenuButton = document.getElementById('uploadMenuButton');
const uploadMenu = document.getElementById('uploadMenu');

// Toggle menu
uploadMenuButton.addEventListener('click', (e) => {
    e.stopPropagation();
    uploadMenu.classList.toggle('hidden');
});

// Click outside to close menu
document.addEventListener('click', (e) => {
    if (!uploadMenu.contains(e.target) && e.target !== uploadMenuButton) {
        uploadMenu.classList.add('hidden');
    }
});

// Close menu after clicking an item
document.querySelectorAll('#uploadMenu a').forEach(item => {
    item.addEventListener('click', () => {
        uploadMenu.classList.add('hidden');
    });
});
