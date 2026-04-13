/* ============================================================
   FitTrack – Dark-mode app logic
   ============================================================ */

(function () {
  "use strict";

  /* ---------- State ---------- */
  const state = {
    filter: "all",
    search: "",
    difficulty: "all",
  };

  /* ---------- DOM refs ---------- */
  const grid = document.getElementById("exerciseGrid");
  const emptyState = document.getElementById("emptyState");
  const searchInput = document.getElementById("searchInput");
  const difficultySelect = document.getElementById("difficultySelect");
  const filterBtns = document.querySelectorAll(".filter-btn");
  const modal = document.getElementById("videoModal");
  const modalTitle = document.getElementById("modalTitle");
  const modalDescription = document.getElementById("modalDescription");
  const modalMuscles = document.getElementById("modalMuscles");
  const videoFrame = document.getElementById("videoFrame");
  const closeModalBtn = document.getElementById("closeModal");
  const exerciseCount = document.getElementById("exerciseCount");

  /* ---------- Render ---------- */
  function render() {
    const filtered = exercises.filter((ex) => {
      const matchCategory =
        state.filter === "all" || ex.category === state.filter;
      const matchDifficulty =
        state.difficulty === "all" || ex.difficulty === state.difficulty;
      const q = state.search.toLowerCase();
      const matchSearch =
        !q ||
        ex.name.toLowerCase().includes(q) ||
        ex.muscles.some((m) => m.toLowerCase().includes(q)) ||
        ex.description.toLowerCase().includes(q);
      return matchCategory && matchDifficulty && matchSearch;
    });

    exerciseCount.textContent = `${filtered.length} exercise${filtered.length !== 1 ? "s" : ""}`;

    grid.innerHTML = "";
    if (filtered.length === 0) {
      emptyState.hidden = false;
    } else {
      emptyState.hidden = true;
      const fragment = document.createDocumentFragment();
      filtered.forEach((ex) => fragment.appendChild(createCard(ex)));
      grid.appendChild(fragment);
    }
  }

  /* ---------- Card builder ---------- */
  function createCard(ex) {
    const card = document.createElement("article");
    card.className = "exercise-card";
    card.setAttribute("role", "button");
    card.setAttribute("tabindex", "0");
    card.setAttribute("aria-label", `Open details for ${ex.name}`);

    const diffLabel = {
      beginner: "Beginner",
      intermediate: "Intermediate",
      advanced: "Advanced",
    }[ex.difficulty];

    card.innerHTML = `
      <div class="card-header">
        <span class="category-badge category-${ex.category}">${capitalise(ex.category)}</span>
        <span class="difficulty-badge difficulty-${ex.difficulty}">${diffLabel}</span>
      </div>
      <h3 class="card-title">${ex.name}</h3>
      <p class="card-description">${ex.description.slice(0, 110)}…</p>
      <ul class="muscle-list">
        ${ex.muscles.map((m) => `<li>${m}</li>`).join("")}
      </ul>
      <div class="card-footer">
        <span class="stat"><span class="stat-label">Sets</span><span class="stat-value">${ex.sets}</span></span>
        <span class="stat"><span class="stat-label">Reps</span><span class="stat-value">${ex.reps}</span></span>
        <button class="watch-btn" data-id="${ex.id}" aria-label="Watch tutorial for ${ex.name}">
          <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14" aria-hidden="true">
            <path d="M8 5v14l11-7z"/>
          </svg>
          Watch
        </button>
      </div>`;

    /* Open modal on click or Enter/Space */
    card.addEventListener("click", (e) => {
      if (!e.target.closest(".watch-btn")) openModal(ex);
    });
    card.addEventListener("keydown", (e) => {
      if ((e.key === "Enter" || e.key === " ") && !e.target.closest(".watch-btn"))
        openModal(ex);
    });
    card.querySelector(".watch-btn").addEventListener("click", (e) => {
      e.stopPropagation();
      openModal(ex);
    });

    return card;
  }

  /* ---------- Modal ---------- */
  function openModal(ex) {
    modalTitle.textContent = ex.name;
    modalDescription.textContent = ex.description;
    modalMuscles.innerHTML = ex.muscles
      .map((m) => `<span class="modal-muscle-tag">${m}</span>`)
      .join("");
    videoFrame.src = `https://www.youtube-nocookie.com/embed/${ex.videoId}?rel=0&modestbranding=1`;
    modal.hidden = false;
    modal.setAttribute("aria-hidden", "false");
    document.body.classList.add("modal-open");
    closeModalBtn.focus();
  }

  function closeModal() {
    modal.hidden = true;
    modal.setAttribute("aria-hidden", "true");
    videoFrame.src = "";
    document.body.classList.remove("modal-open");
  }

  closeModalBtn.addEventListener("click", closeModal);
  modal.addEventListener("click", (e) => {
    if (e.target === modal) closeModal();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !modal.hidden) closeModal();
  });

  /* ---------- Filters ---------- */
  filterBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      filterBtns.forEach((b) => {
        b.classList.remove("active");
        b.setAttribute("aria-pressed", "false");
      });
      btn.classList.add("active");
      btn.setAttribute("aria-pressed", "true");
      state.filter = btn.dataset.category;
      render();
    });
  });

  searchInput.addEventListener("input", debounce(() => {
    state.search = searchInput.value;
    render();
  }, 150));

  difficultySelect.addEventListener("change", () => {
    state.difficulty = difficultySelect.value;
    render();
  });

  /* ---------- Helpers ---------- */
  function capitalise(str) {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }

  function debounce(fn, delay) {
    let timer;
    return function (...args) {
      clearTimeout(timer);
      timer = setTimeout(() => fn.apply(this, args), delay);
    };
  }

  /* ---------- Boot ---------- */
  render();
})();
